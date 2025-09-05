package com.tikitaka.api.batch.goods;

import com.tikitaka.api.batch.forbiddenWord.ForbiddenWordBatchRepository;
import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;
import com.tikitaka.api.batch.inspection.InspectBatchService;
import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchInspectionScheduler {

    private final GoodsBatchRequestRepository requestRepository;
    private final InspectBatchService inspectService;
    private final ForbiddenWordBatchRepository forbiddenWordRepository;

    // 5분마다 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 */5 * * * *")
    public void processPendingBatchRequests() {
        log.info("===== 배치 검수 스케줄러 시작 =====");

        // 1. 처리할 PENDING 상태의 요청을 10개 가져옵니다.
        List<GoodsBatchRequest> pendingRequests = requestRepository.findPendingRequests(10);
        if (pendingRequests.isEmpty()) {
            log.info("처리할 배치 검수 요청이 없습니다.");
            log.info("===== 배치 검수 스케줄러 종료 =====");
            return;
        }

        // 2. 가져온 요청들을 'PROCESSING' 상태로 변경하여 다른 스케줄러가 중복 처리하는 것을 방지합니다.
        List<Long> requestIds = pendingRequests.stream().map(GoodsBatchRequest::getRequestId).collect(Collectors.toList());
        requestRepository.updateStatusToProcessing(requestIds);
        log.info("{}건의 요청을 PROCESSING 상태로 변경했습니다.", requestIds.size());

        // 3. 각 요청을 순회하며 AI 검수를 실행합니다.
        for (GoodsBatchRequest request : pendingRequests) {
            try {
                log.info("--- request_id: {} 검수 처리 시작 ---", request.getRequestId());

                // 3-1. DB 데이터를 AI 검수 서비스가 이해할 수 있는 형태로 변환합니다.
                Goods goods = request.toGoodsEntity();
                List<FileContent> filesToInspect = readFilesFromPaths(request);

                // 3-2. 금칙어 목록을 조회합니다.
                ForbiddenWordSearchParam searchParam = new ForbiddenWordSearchParam();
                searchParam.setLgroup(goods.getLgroup());
                searchParam.setMgroup(goods.getMgroup());
                searchParam.setSgroup(goods.getSgroup());
                searchParam.setDgroup(goods.getDgroup());
                List<ForbiddenWord> forbiddenWordsList = forbiddenWordRepository.findActiveForbiddenWords(searchParam);
                String forbiddenWords = forbiddenWordsList.stream()
                        .map(ForbiddenWord::getWord)
                        .collect(Collectors.joining(","));

                // 3-3. Gemini API 호출
                InspectionResult result = inspectService.performAiInspection(goods, filesToInspect, forbiddenWords);
                log.info("Gemini API 호출 결과: 승인여부 = {}, 사유 = {}", result.isApproved(), result.getReason());

                // 3-4. 결과에 따라 DB 상태를 업데이트합니다.
                if (result.isApproved()) {
                    requestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "COMPLETED", null);
                } else {
                    requestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "FAILED", String.format("코드 : %d, 사유 : %s", result.getErrorCode(), result.getReason()));
                }

            } catch (Exception e) {
                log.error("!! request_id: {} 처리 중 심각한 오류 발생 !!", request.getRequestId(), e);
                requestRepository.updateFinalStatus(request.getRequestId(), "FAILED", "FAILED", e.getMessage());
            } finally {
                log.info("--- request_id: {} 검수 처리 종료 ---", request.getRequestId());
            }
        }
        log.info("===== 배치 검수 스케줄러 종료 =====");
    }

    /**
     * GoodsBatchRequest에 저장된 파일 경로들로부터 실제 파일 내용을 읽어 FileContent 리스트를 생성합니다.
     */
    private List<FileContent> readFilesFromPaths(GoodsBatchRequest request) throws IOException {
        List<FileContent> fileContents = new ArrayList<>();

        // 1. 대표 이미지 읽기
        String repPath = request.getRepresentativeFilePath();
        if (repPath != null && !repPath.isEmpty()) {
            fileContents.add(createFileContent(repPath));
        }

        // 2. 추가 이미지들 읽기 (쉼표로 구분된 경로)
        String addPaths = request.getImageFilesPaths();
        if (addPaths != null && !addPaths.isEmpty()) {
            String[] pathArray = addPaths.split(",");
            for (String pathStr : pathArray) {
                fileContents.add(createFileContent(pathStr.trim()));
            }
        }

        return fileContents;
    }
    
    /**
     * 파일 경로(String)를 받아 실제 파일을 읽고 FileContent 객체를 생성하는 헬퍼 메소드
     */
    private FileContent createFileContent(String filePathStr) throws IOException {
        Path path = Paths.get(filePathStr);
        String fileName = path.getFileName().toString();
        String mimeType = Files.probeContentType(path);
        if (mimeType == null) {
            mimeType = "application/octet-stream"; // 타입을 알 수 없는 경우 기본값
        }
        byte[] content = Files.readAllBytes(path);

        return new FileContent(fileName, mimeType, content);
    }
}