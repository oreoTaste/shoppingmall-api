package com.tikitaka.api.batch.goods;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.tikitaka.api.batch.forbiddenWord.ForbiddenWordBatchRepository;
import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.batch.forbiddenWord.dto.HarmfulwordBatchDto;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;
import com.tikitaka.api.batch.goods.dto.BatchResultPayload;
import com.tikitaka.api.batch.goods.dto.GoodsBatchDto;
import com.tikitaka.api.batch.goods.entity.Goods;
import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;
import com.tikitaka.api.batch.image.ImageDownloadBatchService;
import com.tikitaka.api.batch.image.ImageSplittingBatchService;
import com.tikitaka.api.batch.image.dto.UrlMultipartFile;
import com.tikitaka.api.batch.inspection.InspectBatchService;
import com.tikitaka.api.batch.inspection.dto.FileContent;
import com.tikitaka.api.batch.inspection.dto.InspectionResult;
import com.tikitaka.api.batch.inspection.dto.InspectionResultReq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.apache.commons.io.input.BOMInputStream;
import org.springframework.beans.factory.annotation.Value;
import reactor.util.retry.Retry;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsBatchService {

    @Value("${file.upload-dir}")
    private String uploadDir;
    
    @Value("${file.download-dir}")
    private String downloadUrl;
    
    @Value("${cloud.aws.bucket.name}")
    private String s3BucketName;

    @Value("${cloud.aws.folder.name}")
    private String s3FolderName;
    
    @Value("${batch.result.callback-url}")
    private String callbackUrl;
    
    @Value("${batch.result.monitoring-yn}")
    private String monitoringYn = "Y";
    
    @Value("${batch.result.monitoring-url}")
    private String monitoringUrl;

    @Value("${batch.max-tries}")
    private int MAX_RETRIES;

    private final GoodsBatchRequestRepository goodsBatchRequestRepository;
    private final AmazonS3 amazonS3; // V1 SDK의 S3 Client
    private final InspectBatchService inspectService;
    private final ImageDownloadBatchService imageDownloadService;
    private final ImageSplittingBatchService imageSplittingService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ForbiddenWordBatchRepository forbiddenWordBatchRepository;

    
    @Async
    public boolean processHarmfulwordsBatch(MultipartFile zipFile) {
    	String originalFileName = Path.of(zipFile.getOriginalFilename()).getFileName().toString();
    	
        Path permanentDir = Paths.get(uploadDir, "batch", originalFileName).toAbsolutePath().normalize();
        log.info("========== 금칙어 배치 처리 시작: zip 파일명 {} ==========", originalFileName);

        try {
            Files.createDirectories(permanentDir);
            log.info("processHarmfulwordsBatch 1. 영구 저장 디렉토리 생성 완료: {}", permanentDir);

            unzip(zipFile.getInputStream(), permanentDir);
            log.info("processHarmfulwordsBatch 2. ZIP 파일 압축 해제 완료.");

            File tsvFile = findTsvFile(permanentDir);
            log.info("processHarmfulwordsBatch 3. TSV 파일 찾기 완료: {}", tsvFile.getAbsolutePath());

            // [핵심] 가장 안정적인 RFC4180Parser를 사용하는 파싱 메소드 호출
            List<HarmfulwordBatchDto> harmfulwordDtoList = GoodsBatchService.parseTsvToDto(tsvFile, HarmfulwordBatchDto.class);
            log.info("processHarmfulwordsBatch 4. TSV 파싱 완료. 총 {}개의 상품 데이터 발견.", harmfulwordDtoList.size());
            
            if (harmfulwordDtoList.isEmpty()) {
                log.warn("경고: TSV 파일에서 상품 데이터를 읽어오지 못했습니다.");
                return true;
            }

            log.info("샘플 데이터 : {}", harmfulwordDtoList.get(0).toString());
            List<ForbiddenWord> forbiddenWords = harmfulwordDtoList.stream().map(HarmfulwordBatchDto::toForbiddenWord).toList();
            
            forbiddenWordBatchRepository.saveAll(forbiddenWords);
            log.info("processHarmfulwordsBatch 5. 총 {}건의 상품 검수 요청을 DB에 성공적으로 저장했습니다.", forbiddenWords.size());
            return true;

        } catch (Exception e) {
            log.error("!! 상품 배치 처리 중 심각한 오류 발생 (Job ID: {}) !!", originalFileName, e);
            return false;
        } finally {
    		try {
    			boolean deleted =FileSystemUtils.deleteRecursively(permanentDir); // 이부분에서 오류가 발생
    			if(deleted) {
                    log.info("processHarmfulwordsBatch 6. 금칙어 파일 경로 정상 삭제했습니다. zip 파일명 {} ", originalFileName);    				
    			} else {
                    log.info("processHarmfulwordsBatch 6. 금칙어 파일 경로 삭제 실패했습니다. zip 파일명 {} ", originalFileName);
    			}
                
    		} catch(IOException e) {
    			log.error("processHarmfulwordsBatch 6. 금칙어 파일 경로 삭제 중 I/O 오류 발생. zip 파일명 {} ", originalFileName, e);
    		}
    		
            log.info("========== 금칙어 배치 처리 종료: zip 파일명 {} ==========", originalFileName);
        }
    }
    
    @Async
    public boolean processGoodsInspectionBatch(MultipartFile zipFile) {
        String batchJobId = UUID.randomUUID().toString();
        Path permanentDir = Paths.get(uploadDir, "batch", batchJobId).toAbsolutePath().normalize();
        log.info("========== 상품 배치 처리 시작: Job ID {} ==========", batchJobId);

        try {
            Files.createDirectories(permanentDir);
            log.info("processGoodsInspectionBatch 1. 영구 저장 디렉토리 생성 완료: {}", permanentDir);

            unzip(zipFile.getInputStream(), permanentDir);
            log.info("processGoodsInspectionBatch 2. ZIP 파일 압축 해제 완료.");

            File tsvFile = findTsvFile(permanentDir);
            log.info("processGoodsInspectionBatch 3. TSV 파일 찾기 완료: {}", tsvFile.getAbsolutePath());

            List<GoodsBatchDto> goodsDtoList = GoodsBatchService.parseTsvToDto(tsvFile, GoodsBatchDto.class);
            log.info("processGoodsInspectionBatch 4. TSV 파싱 완료. 총 {}개의 상품 데이터 발견.", goodsDtoList.size());

            if (goodsDtoList.isEmpty()) {
                log.warn("경고: TSV 파일에서 상품 데이터를 읽어오지 못했습니다.");
                return true;
            }

            log.info("샘플 데이터 : {}", goodsDtoList.get(0).toString());
            List<GoodsBatchRequest> requestEntities = new ArrayList<>();
            for (GoodsBatchDto dto : goodsDtoList) {
                BigDecimal salePrice = BigDecimal.ZERO;
                BigDecimal buyPrice = BigDecimal.ZERO;
                try {
                    if (StringUtils.hasText(dto.getSalePrice())) {
                        salePrice = new BigDecimal(dto.getSalePrice());
                    }
                    if (StringUtils.hasText(dto.getBuyPrice())) {
                        buyPrice = new BigDecimal(dto.getBuyPrice());
                    }
                } catch (NumberFormatException e) {
                    log.error("숫자 변환 실패! 상품명: '{}', salesPrice: '{}', buyPrice: '{}'. 가격을 0으로 처리합니다.",
                            dto.getGoodsName(), dto.getSalePrice(), dto.getBuyPrice());
                }
                
                String repFilePath = dto.getRepresentativeFile().toString();

                GoodsBatchRequest requestEntity = GoodsBatchRequest.builder()
                        .batchJobId(batchJobId)
                        .goodsCode(dto.getGoodsCode())
                        .goodsName(dto.getGoodsName())
                        .mobileGoodsName(dto.getMobileGoodsName())
                        .salePrice(salePrice)
                        .buyPrice(buyPrice)
                        .goodsInfo(dto.getGoodsInfo())
                        .imageHtml(dto.getImageHtml())
                        .representativeFile(repFilePath)
                        .lgroup(dto.getLgroup())
                        .lgroupName(dto.getLgroupName())
                        .mgroup(dto.getMgroup())
                        .mgroupName(dto.getMgroupName())
                        .sgroup(dto.getSgroup())
                        .sgroupName(dto.getSgroupName())
                        .dgroup(dto.getDgroup())
                        .dgroupName(dto.getDgroupName())
                        .build();

                requestEntities.add(requestEntity);
            }

            goodsBatchRequestRepository.saveAll(requestEntities);
            log.info("processGoodsInspectionBatch 5. 총 {}건의 상품 검수 요청을 DB에 성공적으로 저장했습니다.", requestEntities.size());
            return true;

        } catch (Exception e) {
            log.error("!! 상품 배치 처리 중 심각한 오류 발생 (Job ID: {}) !!", batchJobId, e);
            return false;
        } finally {
            log.info("========== 상품 배치 처리 종료: Job ID {} ==========", batchJobId);
        }
    }

    @Async
    public void processPendingBatchRequests(int batchCount) {
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    	String s3ReceivedResult = getBatchInStatus(todayDate);
    	// s3에서 데이터를 정확히 받은 시점부터 구동되도록 처리
    	if(!s3ReceivedResult.equals("SUCCESS")) {
    		return;
    	}
    	
    	if(batchCount <= 0 || batchCount > 1000) {
    		batchCount = 100;
    	}
    	
    	log.info("===== 배치 검수 스케줄러 시작 (Thread: {}) =====", Thread.currentThread().getName());
    	
        // 1. 처리할 PENDING 상태의 요청을 {batchCount}개 가져옵니다.
        List<GoodsBatchRequest> pendingRequests = goodsBatchRequestRepository.findPendingRequests(batchCount);
        if (pendingRequests.isEmpty()) {
            log.info("처리할 배치 검수 요청이 없습니다.");
            log.info("===== 배치 검수 스케줄러 종료 =====");
            return;
        }

        // 2. 가져온 요청들을 'PROCESSING' 상태로 변경하여 다른 스케줄러가 중복 처리하는 것을 방지합니다.
        List<Long> requestIds = pendingRequests.stream().map(GoodsBatchRequest::getRequestId).collect(Collectors.toList());
        goodsBatchRequestRepository.updateStatusToProcessing(requestIds);
        log.info("{}건의 요청을 PROCESSING 상태로 변경했습니다.", requestIds.size());

        // 3. 각 요청을 순회하며 AI 검수를 실행합니다.
//        pendingRequests.parallelStream().forEach(request -> {
        for (GoodsBatchRequest request : pendingRequests) {
            try {
                log.debug("--- request_id: {} 검수 처리 시작 ---", request.getRequestId());

                // 3-1. DB 데이터를 AI 검수 서비스가 이해할 수 있는 형태로 변환합니다.
                Goods goods = request.toGoodsEntity();
                List<FileContent> filesToInspect = readFilesFromPaths(request);

                // 3-2. 금칙어 목록을 조회합니다.
                ForbiddenWordSearchParam searchParam = new ForbiddenWordSearchParam();
                searchParam.setLgroup(goods.getLgroup());
                searchParam.setMgroup(goods.getMgroup());
                searchParam.setSgroup(goods.getSgroup());
                searchParam.setDgroup(goods.getDgroup());
                List<ForbiddenWord> forbiddenWordsList = forbiddenWordBatchRepository.findActiveForbiddenWords(searchParam);
                String forbiddenWords = forbiddenWordsList.stream()
                        .map(ForbiddenWord::getWord)
                        .collect(Collectors.joining(","));

                if(forbiddenWords.length() <= 0) {
                	// 3-3. 금칙어가 없는 경우 정상종료처리
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("COMPLETED");
                	request.setErrorMessage(null);
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "COMPLETED", null, "금칙어가 없습니다.");
                	continue;
//                	return;
                }
                
                // 3-3. Gemini API 호출
                InspectionResult inspectionResult = inspectService.performAiInspection(goods, filesToInspect, forbiddenWords);
                log.debug("Gemini API 호출 결과: 승인여부 = {}, 사유 = {}", inspectionResult.isApproved(), inspectionResult.getReason());
                
                // 3-4. 결과에 따라 DB 상태를 업데이트합니다.
                if (inspectionResult.isApproved()) {
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("COMPLETED");
                	request.setForbiddenWord(inspectionResult.getForbiddenWord());
                	request.setErrorMessage(null);
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "COMPLETED", null, null);
                } else {
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("FAILED");
                	request.setForbiddenWord(inspectionResult.getForbiddenWord());
                	request.setErrorMessage(inspectionResult.getReason());
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "FAILED", inspectionResult.getForbiddenWord(), inspectionResult.getReason());
                }

            } catch (Exception e) {
                int currentRetries = request.getRetries();
                
                // 재시도 횟수가 최대 횟수 미만인 경우
                if (currentRetries < MAX_RETRIES) {
                    log.info("!! request_id: {} 처리 중 오류 발생. 재시도를 위해 상태를 PENDING으로 변경합니다. (시도: {}) !!", 
                             request.getRequestId(), currentRetries + 1, e.getMessage());
                    // 재시도 횟수를 1 증가시키고 상태를 다시 PENDING으로 업데이트합니다.
                    goodsBatchRequestRepository.incrementRetryCount(request.getRequestId(), e.getMessage());
                } else {
                	// 2. 실패 확정 로직 (최대 횟수 초과)
                	String finalErrorMessage = e.getMessage(); // 기본값: 예외 메시지

                    // [개선 1] WebClient 에러인 경우 JSON 파싱 시도
                    if (e instanceof WebClientResponseException wce) {
                        String responseBody = wce.getResponseBodyAsString(StandardCharsets.UTF_8);
                        try {
                            // 예상 구조: { "error": { "message": "Provided image is not valid.", ... } }
                            JsonNode rootNode = objectMapper.readTree(responseBody);
                            
                            if (rootNode.path("error").path("message").isTextual()) {
                                finalErrorMessage = rootNode.path("error").path("message").asText();
                            } else {
                                finalErrorMessage = responseBody;
                            }
                        } catch (Exception jsonEx) {
                            finalErrorMessage = responseBody;
                        }
                    }
                    
                    if (finalErrorMessage != null && finalErrorMessage.length() > 200) {
                        finalErrorMessage = finalErrorMessage.substring(0, 195) + "...";
                    }

                    // 로그에는 원본 예외와 추출한 메시지를 모두 남김
                    log.error("!! request_id: {} 최종 실패. ErrorMsg: {}", request.getRequestId(), finalErrorMessage);

                    goodsBatchRequestRepository.updateFinalStatus(
                        request.getRequestId(), 
                        "FAILED", 
                        "FAILED", 
                        null, 
                        finalErrorMessage
                    );
                    
                    request.setErrorMessage(finalErrorMessage);
                }
            } finally {
                log.info("--- request_id: {} 검수 처리 종료 ---", request.getRequestId());
            }
        }
//        );
        
    	// 결과전송 메서드 호출
        sendBatchResult(pendingRequests);
        
        log.info("===== 배치 검수 스케줄러 종료 =====");
    }

    /**
     * 배치 처리 결과 리스트를 지정된 URL로 전송합니다.
     * @param completedRequests 전송할 배치 요청 데이터 리스트
     */
    private void sendBatchResult(List<GoodsBatchRequest> completedRequests) {
        log.info(">>> 배치 결과 전송 메서드 sendBatchResult 시작 (총 {}건)", completedRequests.size());
        if (completedRequests == null || completedRequests.isEmpty()) {
            log.warn("전송할 배치 결과 데이터가 없습니다. 메서드를 종료합니다.");
            return;
        }

        List<BatchResultPayload> payloads = null;
        String jsonPayload = null;

        try {
            log.info("1. GoodsBatchRequest를 BatchResultPayload DTO로 변환 시작...");
            payloads = completedRequests.stream()
                    .map(BatchResultPayload::from)
                    .collect(Collectors.toList());
            log.info("   - DTO 변환 완료. 변환된 객체 수: {}", payloads.size());

            log.info("2. DTO 리스트를 JSON 문자열로 직렬화 시작...");
            jsonPayload = objectMapper.writeValueAsString(payloads);
            log.debug("   - 직렬화된 JSON 페이로드: {}", jsonPayload); // DEBUG 레벨로 페이로드 로깅
            log.info("   - JSON 직렬화 완료.");

        } catch (JsonProcessingException e) {
            log.error("!! JSON 직렬화 중 심각한 오류 발생 !!", e);
            return; // 직렬화 실패 시, 더 이상 진행하지 않고 메서드 종료
        }

        try {
        	// -------------------------------------------------------
            // [기존] 1. 메인 콜백 서버로 상세 결과 전송
            // -------------------------------------------------------
        	log.info("3. WebClient를 사용하여 콜백 URL로 결과 전송 시작. URL: {}", callbackUrl);
            WebClient webClient = webClientBuilder.build();

            webClient.post()
                     .uri(callbackUrl)
                     .contentType(MediaType.APPLICATION_JSON)
                     .body(Mono.just(jsonPayload), String.class)
                     .retrieve()
                     .bodyToMono(String.class)
                     .timeout(Duration.ofSeconds(10))
                     .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                     .doOnSubscribe(subscription -> log.info("   - 콜백 API에 대한 비동기 요청 구독 시작..."))
                     .doOnSuccess(response ->
                        log.info("   - ✅ {}건의 배치 결과 전송 성공. 서버 응답: {}", completedRequests.size(), response))
                     .doOnError(error -> {
                         String failedIds = completedRequests.stream()
                                 .map(r -> String.valueOf(r.getRequestId()))
                                 .collect(Collectors.joining(","));
                         log.error("!! ❌ 배치 결과 전송 최종 실패 !! (대상 ID 목록: {}) 원인: {}", failedIds, error.getMessage());
                      })
                     .doFinally(signalType ->
                        log.info("   - 비동기 요청 스트림 종료. Signal: {}", signalType))
                     .subscribe();

        } catch (Exception e) {
            log.error("!! WebClient 요청 설정 또는 실행 중 예외 발생 !!", e);
        }

        final int payloadSize = payloads.size();
        try {
            // -------------------------------------------------------
            // [추가] 2. 모니터링 서버로 처리 건수 전송
            // -------------------------------------------------------
        	log.info("4. WebClient를 사용하여 모니터링 URL로 통계 전송 시작. URL: {}", monitoringUrl);
        	
        	if(!monitoringYn.equalsIgnoreCase("Y")) {
                log.info("<<< 배치 결과 전송 메서드 sendBatchResult 종료 (모니터링 로그x)");
        		return;
        	}
            
            // 전송할 데이터 (예: "300" 문자열)
        	Map<String, Object> monitoringBody = new HashMap<>();
        	String today = LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        	
        	monitoringBody.put("monitoringName", "ai 검수결과 (" + today + ")");
        	monitoringBody.put("count", payloadSize);
            
            WebClient webClient = webClientBuilder.build();
            webClient.post()
                     .uri(monitoringUrl)
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(monitoringBody)
                     .retrieve()
                     .bodyToMono(String.class)
                     .timeout(Duration.ofSeconds(10))
                     .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                     .doOnSubscribe(s -> log.info("   - 모니터링 전송 시도..."))
                     .doOnSuccess(res -> log.info("   - ✅ 모니터링 통계 전송 성공 ({}건).", payloadSize))
                     .doOnError(error -> {
                         // 모니터링 실패는 메인 로직만큼 치명적이지 않을 수 있으므로 로그만 남김
                         log.warn("!! ❌ 모니터링 전송 실패 !! 원인: {}", error.getMessage());
                      })
                     .subscribe();

        } catch (Exception e) {
            log.error("!! 모니터링 WebClient 요청 설정 중 예외 발생 !!", e);
        }

        log.info("<<< 배치 결과 전송 메서드 sendBatchResult 종료");
    }
    
    /**
     * GoodsBatchRequest에 저장된 파일 경로들로부터 실제 파일 내용을 읽어 FileContent 리스트를 생성합니다.
     */
    private List<FileContent> readFilesFromPaths(GoodsBatchRequest request) throws IOException {
        List<FileContent> fileContents = new ArrayList<>();

        // 1. 대표 이미지 읽기
        String repPath = request.getRepresentativeFile(); // https://img.shoppingntmall.com + /goods/783/23978783_h.jpg
        if (repPath != null && !repPath.isEmpty()) {
            
            // 1-1. 이미지 다운로드 (s3서버에서 다운로드)
            List<String> imageUrlList = Arrays.stream(repPath.split(","))
                    .map(path -> downloadUrl + path.trim().replace(" ", "%20")) // 각 경로 앞에 URL 붙이기 및 공백 제거
                    .collect(Collectors.toList());
            
            MultipartFile[] downloadedFiles = imageDownloadService.downloadImagesAsMultipartFiles(imageUrlList);
            
            // 1-2. 분할된 이미지를 FileContent로 변환
            for (MultipartFile file : downloadedFiles) {
            	// GIF 파일은 AI 검수에서 제외 (Unsupported MIME type 에러 방지)
                if (file.getContentType() != null && file.getContentType().toLowerCase().contains("image/gif")) {
                    log.warn("GIF 이미지는 AI 검수 대상에서 제외됩니다. 파일명: {}", file.getOriginalFilename());
                    continue;
                }
                
                fileContents.add(new FileContent(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            }
        }
        
        // 2. image_html의 URL들을 읽어 다운로드
        String imageUrls = request.getImageHtml();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            // 2-1. 다운로드된 이미지 분할
            List<String> imageUrlList = Arrays.asList(imageUrls.split(","));
            MultipartFile[] downloadedFiles = imageDownloadService.downloadImagesAsMultipartFiles(imageUrlList);
            
            MultipartFile[] splittedImages = imageSplittingService.splitImages(downloadedFiles, 1600); // 1600px 높이로 분할

            // 2-2. 분할된 이미지를 FileContent로 변환
            for (MultipartFile file : splittedImages) {
            	// GIF 파일은 AI 검수에서 제외 (Unsupported MIME type 에러 방지)
                if (file.getContentType() != null && file.getContentType().toLowerCase().contains("image/gif")) {
                    log.warn("GIF 이미지는 AI 검수 대상에서 제외됩니다. 파일명: {}", file.getOriginalFilename());
                    continue;
                }
                
                fileContents.add(new FileContent(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            }
        }

        return fileContents;        
    }

    /**
     * Tsv파일을 DTO 리스트로 파싱하는 메서드
     *
     * @param tsvFile  파싱할 TSV 파일
     * @param dtoClass 변환할 DTO 클래스
     * @param <T>      DTO 타입
     * @return DTO 객체 리스트
     * @throws IOException 파일 읽기 오류 시
     */
    public static <T> List<T> parseTsvToDto(File tsvFile, Class<T> dtoClass) throws IOException {
        // 1. TSV 파서(구분자: 탭)를 생성합니다.
        CSVParser tsvParser = new CSVParserBuilder()
                .withSeparator('\t') // 핵심: 구분자를 쉼표(,) 대신 탭(\t)으로 변경
                // .withQuoteChar('\"') // TSV도 CSV처럼 따옴표 문자를 사용할 수 있습니다. 기본값은 '\"'입니다.
                .build();

        // 2. [유지] FileInputStream을 BOMInputStream으로 감싸서 BOM을 자동으로 제거합니다.
        // (TSV 파일도 BOM이 있을 수 있습니다)
        try (BOMInputStream bomInputStream = new BOMInputStream(new FileInputStream(tsvFile));
             InputStreamReader reader = new InputStreamReader(bomInputStream, StandardCharsets.UTF_8)) {

            // 3. 위에서 만든 TSV 파서를 사용하여 CSV 리더(Reader)를 생성합니다.
            // (클래스 이름이 CSVReader이지만, 지정된 파서를 따르므로 TSV 처리가 가능합니다)
            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(tsvParser) // 수정된 tsvParser를 주입
                    .build();

            // 4. [유지] DTO의 헤더 이름과 TSV의 헤더를 매핑하는 전략을 설정합니다.
            HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(dtoClass);

            // 5. [유지] CsvToBeanBuilder에 리더와 매핑 전략을 제공하여 최종 변환합니다.
            return new CsvToBeanBuilder<T>(csvReader)
                    .withMappingStrategy(strategy)
                    .build()
                    .parse();
        }
    }

    private void unzip(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(zipEntry.getName());
                if (!newPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("ZIP 파일에 올바르지 않은 경로가 포함되어 있습니다: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private File findTsvFile(Path dir) throws FileNotFoundException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tsv")) {
            for (Path path : stream) {
                return path.toFile();
            }
        } catch (IOException e) {
            throw new FileNotFoundException("TSV 파일을 찾는 중 오류가 발생했습니다.");
        }
        throw new FileNotFoundException("디렉토리에서 TSV 파일을 찾을 수 없습니다: " + dir);
    }
    
    @Async
    public void removeOldRecord(int day) {
    	List<String> batchJobIdList = goodsBatchRequestRepository.findOldBatchRecord(day);
    	
    	for(String batchJobId : batchJobIdList) {
 	       if (batchJobId == null || batchJobId.trim().isEmpty()) {
 	            log.warn("삭제할 batchJobId가 제공되지 않았습니다.");
 	            return;
 	        }

 	        // 1. 삭제할 디렉토리의 전체 경로를 구성합니다.
 	        Path directoryToDelete = Paths.get(uploadDir, "batch", batchJobId).toAbsolutePath().normalize();
 	        log.info("'{}' 배치 작업 디렉토리 삭제를 시도합니다. 경로: {}", batchJobId, directoryToDelete);

 	        try {
 	            // 2. FileSystemUtils.deleteRecursively를 사용하여 디렉토리와 내용을 재귀적으로 삭제합니다.
 	            //    디렉토리가 존재하지 않아도 예외가 발생하지 않고 false를 반환합니다.
 	            boolean deleted = FileSystemUtils.deleteRecursively(directoryToDelete);

 	            if (deleted) {
 	                log.info("배치 작업 디렉토리 '{}'를 성공적으로 삭제했습니다.", batchJobId);
 	            } else {
 	                log.warn("삭제할 배치 작업 디렉토리 '{}'를 찾을 수 없거나 이미 삭제되었습니다.", batchJobId);
 	            }
 	        } catch (IOException e) {
 	            // 파일 권한 문제 등 예기치 않은 I/O 오류 발생 시 처리
 	            log.error("!! 배치 작업 디렉토리 '{}' 삭제 중 오류가 발생했습니다. !!", batchJobId, e);
 	            // 필요에 따라 사용자 정의 예외를 던져 트랜잭션 롤백 등을 처리할 수 있습니다.
 	            // throw new RuntimeException("디렉토리 삭제에 실패했습니다.", e);
 	        }
 	        
 	        // 2. 삭제한 이후 db에서 기록도 삭제
 	       goodsBatchRequestRepository.deleteOldBatchRecord(3);
    	}
    }

	public boolean gatherS3Data(String todayDate) throws Exception{
        log.info("gatherS3Data 1. S3 버킷 '{}'의 '{}' 폴더에서 객체 목록 조회를 시작합니다.", s3BucketName, s3FolderName);

        // 객체 목록을 조회합니다.
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(s3BucketName)
                .withPrefix(s3FolderName);
        ObjectListing objectListing = amazonS3.listObjects(listObjectsRequest);
        List<S3ObjectSummary> s3Objects = objectListing.getObjectSummaries();

        log.info("   - {}개의 객체를 S3 버킷의 '{}' 폴더에서 발견했습니다.", s3Objects.size(), s3FolderName);

        if (s3Objects.isEmpty()) {
            log.info("gatherS3Data 2. 처리할 파일이 S3 폴더에 없습니다. 스케줄러를 종료합니다.");
            return true;
        }

        int zipFileCount = 0;
        for (S3ObjectSummary s3Object : s3Objects) {
            String key = s3Object.getKey();
            log.info("gatherS3Data 3. 객체 확인 중: '{}' (크기: {} bytes)", key, s3Object.getSize());

            if (key.equals(s3FolderName)) {
                log.info("   - '{}'은(는) 폴더이므로 건너뜁니다.", key);
                continue;
            }

            if (!key.toLowerCase().endsWith(".zip")) {
                log.info("   - '{}'은(는) ZIP 파일이 아니므로 건너뜁니다.", key);
                continue;
            }
            
            // 파일형식 : goods_ai_inspection_yyyyMMdd.zip (예시. goods_ai_inspection_20250926.zip)
            // 파일명에 오늘날짜가 포함되어있으면 해당 파일을 처리
            if (!key.contains(todayDate)) { // 파일명에 오늘 날짜가 포함되어 있지 않으면 건너뜀
            	// 파일명에서 "yyyyMMdd" 패턴을 찾아 오늘 날짜와 비교하는 더 안전한 로직
                log.info("   - '{}'은(는) 오늘자 ZIP 파일이 아니므로 건너깁니다. (오늘 날짜: {})", key, todayDate);
                continue;
            }
            
            if(key.contains("harmfulword_")) {
                zipFileCount++;
                log.info("   - '{}'은(는) ZIP 파일입니다. 다운로드를 시작합니다.", key);
                try {
                    GetObjectRequest getObjectRequest = new GetObjectRequest(s3BucketName, key);
                    com.amazonaws.services.s3.model.S3Object object = amazonS3.getObject(getObjectRequest);
                    byte[] data = IOUtils.toByteArray(object.getObjectContent());
                    log.info("   - '{}' 파일 다운로드 완료 ({} bytes).", key, data.length);

                    log.info("gatherS3Data 4. 다운로드한 파일을 MultipartFile 객체로 변환합니다.");
                    MultipartFile multipartFile = new UrlMultipartFile(data, key, "application/zip");
                    log.info("   - MultipartFile 객체 생성 완료: '{}'", multipartFile.getOriginalFilename());

                    log.info("gatherS3Data 5. GoodsBatchService의 processHarmfulwordsBatch 메서드를 호출하여 금칙어 동기화 배치를 시작합니다.");
                    boolean batchResult = processHarmfulwordsBatch(multipartFile);
                    if(!batchResult) {
                    	return false;
                    }
                    log.info("   - '{}' 파일에 대한 배치 처리 요청이 성공적으로 전달되었습니다.", key);

                } catch (Exception e) {
                    log.error("!! '{}' 파일 처리 중 오류가 발생했습니다. 다음 파일로 넘어갑니다.", key, e);
                    throw e;
                }
            } else if(key.contains("goods_ai_inspection_")) {
                zipFileCount++;
                log.info("   - '{}'은(는) ZIP 파일입니다. 다운로드를 시작합니다.", key);
                try {
                    GetObjectRequest getObjectRequest = new GetObjectRequest(s3BucketName, key);
                    com.amazonaws.services.s3.model.S3Object object = amazonS3.getObject(getObjectRequest);
                    byte[] data = IOUtils.toByteArray(object.getObjectContent());
                    log.info("   - '{}' 파일 다운로드 완료 ({} bytes).", key, data.length);

                    log.info("gatherS3Data 4. 다운로드한 파일을 MultipartFile 객체로 변환합니다.");
                    MultipartFile multipartFile = new UrlMultipartFile(data, key, "application/zip");
                    log.info("   - MultipartFile 객체 생성 완료: '{}'", multipartFile.getOriginalFilename());

                    log.info("gatherS3Data 5. GoodsBatchService의 processGoodsInspectionBatch 메서드를 호출하여 상품 검수 배치를 시작합니다.");
                    boolean batchResult = processGoodsInspectionBatch(multipartFile);
                    if(!batchResult) {
                    	return false;
                    }
                    log.info("   - '{}' 파일에 대한 배치 처리 요청이 성공적으로 전달되었습니다.", key);

                } catch (Exception e) {
                    log.error("!! '{}' 파일 처리 중 오류가 발생했습니다. 다음 파일로 넘어갑니다.", key, e);
                    throw e;
                }
            }

        }
        log.info("gatherS3Data 6. 총 {}개의 ZIP 파일을 처리했습니다.", zipFileCount);
        return true;
	}
	
	/**
	 * @return 배치상태 확인(ERROR:오류, NONE:기록없음, PENDING:배치수행중, SUCCESS:배치성공)
	 * */
	public String getBatchInStatus(String yyyymmdd) {
		try {
			// PENDING(시작), SUCCESS(성공종료) 혹은 NONE(이력없음)을 리턴
			HashMap<String, Object> statusResult = goodsBatchRequestRepository.selectDailyStatus(yyyymmdd);
			if(statusResult.isEmpty()) {
				return "NONE";
			}
			
			String status = (String) statusResult.get("status");
			int cnt = (int) statusResult.get("cnt");

			// cnt가 1을 초과하는 경우
			if(cnt > 1) {
				return "ERROR";				
			}
			
			// cnt가 0인 경우
			if(cnt <= 0) {
				return "NONE";
			}
			
			// status가 pending이거나 success인 경우
			if(status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("SUCCESS")) {
				return status;				
			}
		} catch(Exception e) {
            log.error("배치 상태 기록 중 오류가 발생했습니다 : getBatchInStatus : 오류코드:{}", e.getMessage());			
		}
		return "ERROR";
	}
	
	/**
	 * status : PENDING-인입시작 기록, SUCCESS/FAILED-인입결과 기록
	 * */
	public boolean recordBatchInStatus(String status) {
		try {
			if(status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("SUCCESS")) {
				return goodsBatchRequestRepository.mergeDailyStatus(status);
			}			
		} catch(Exception e) {
            log.error("배치 상태 기록 중 오류가 발생했습니다 : recordBatchInStatus : 상태코드:{}, 오류코드: {}", status, e.getMessage());
		}
		return false;
	}

   	public List<GoodsBatchRequest> getGoodsBatchResult(InspectionResultReq inspectionResultReq) {
		List<GoodsBatchRequest> statusResult = goodsBatchRequestRepository.selectGoodsBatchRequest(inspectionResultReq);
		return statusResult;
	}

	public void sendMonitoringEventsAlive() {
        log.debug(">>> 모니터링용 결과 전송 : sendMonitoringEventsAlive 시작");
        
        try {
        	if(!monitoringYn.equalsIgnoreCase("Y")) {
                log.debug("<<< 모니터링용 결과 전송 안함 : sendMonitoringEventsAlive 종료");
        		return;
        	}
        	
        	log.debug("[sendMonitoringEventsAlive] WebClient를 사용하여 모니터링 URL로 통계 전송 시작. URL: {}", monitoringUrl);
            
        	Map<String, Object> monitoringBody = new HashMap<>();
        	monitoringBody.put("monitoringName", "ai 생존여부");
        	monitoringBody.put("count", 0);
            
            WebClient webClient = webClientBuilder.build();
            webClient.post()
                     .uri(monitoringUrl)
                     .contentType(MediaType.APPLICATION_JSON)
                     .bodyValue(monitoringBody)
                     .retrieve()
                     .bodyToMono(String.class)
                     .timeout(Duration.ofSeconds(10))
                     .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                     .doOnSubscribe(s -> log.info("   - 모니터링 전송 시도..."))
                     .doOnSuccess(res -> log.info("   - ✅ 모니터링 통계 전송 성공"))
                     .doOnError(error -> {
                         log.warn("[sendMonitoringEventsAlive] ❌ 모니터링 전송 실패 !! 원인: {}", error.getMessage());
                      })
                     .subscribe();

        } catch (Exception e) {
            log.error("[sendMonitoringEventsAlive] 모니터링 WebClient 요청 설정 중 예외 발생 !!", e);
        }

        log.debug("<<< 모니터링용 결과 전송 : sendMonitoringEventsAlive 종료");
	}

}