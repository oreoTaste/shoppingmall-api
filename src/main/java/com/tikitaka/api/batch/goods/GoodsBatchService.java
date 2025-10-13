package com.tikitaka.api.batch.goods;

import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.tikitaka.api.batch.forbiddenWord.ForbiddenWordBatchRepository;
import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
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

    private final GoodsBatchRequestRepository goodsBatchRequestRepository;
    private final AmazonS3 amazonS3; // V1 SDK의 S3 Client
    private final InspectBatchService inspectService;
    private final ImageDownloadBatchService imageDownloadService;
    private final ImageSplittingBatchService imageSplittingService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ForbiddenWordBatchRepository forbiddenWordBatchRepository;
    private static final int MAX_RETRIES = 3; // 재시도 횟수 상수로 정의
    
    @Async
    public boolean processGoodsInspectionBatch(MultipartFile zipFile) {
        String batchJobId = UUID.randomUUID().toString();
        Path permanentDir = Paths.get(uploadDir, "batch", batchJobId).toAbsolutePath().normalize();
        log.info("========== 상품 배치 처리 시작: Job ID {} ==========", batchJobId);

        try {
            Files.createDirectories(permanentDir);
            log.info("1. 영구 저장 디렉토리 생성 완료: {}", permanentDir);

            unzip(zipFile.getInputStream(), permanentDir);
            log.info("2. ZIP 파일 압축 해제 완료.");

            File csvFile = findCsvFile(permanentDir);
            log.info("3. CSV 파일 찾기 완료: {}", csvFile.getAbsolutePath());

            // [핵심] 가장 안정적인 RFC4180Parser를 사용하는 파싱 메소드 호출
            List<GoodsBatchDto> goodsDtoList = parseCsvToDto(csvFile);
            log.info("4. CSV 파싱 완료. 총 {}개의 상품 데이터 발견.", goodsDtoList.size());

            if (goodsDtoList.isEmpty()) {
                log.warn("경고: CSV 파일에서 상품 데이터를 읽어오지 못했습니다.");
                return true;
            }

            List<GoodsBatchRequest> requestEntities = new ArrayList<>();
            for (GoodsBatchDto dto : goodsDtoList) {
            	
                // [핵심] 안전한 숫자 변환 로직
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
                
//                String repFilePath = permanentDir.resolve("images/" + dto.getRepresentativeFile()).toString();
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
            log.info("5. 총 {}건의 상품 검수 요청을 DB에 성공적으로 저장했습니다.", requestEntities.size());
            return true;

        } catch (Exception e) {
            log.error("!! 상품 배치 처리 중 심각한 오류 발생 (Job ID: {}) !!", batchJobId, e);
            return false;
        } finally {
            log.info("========== 상품 배치 처리 종료: Job ID {} ==========", batchJobId);
        }
    }

    public void processPendingBatchRequests(int batchCount) {
    	if(batchCount <= 0 || batchCount > 1000) {
    		batchCount = 100;
    	}
    	
        log.info("===== 배치 검수 스케줄러 시작 =====");

        // 1. 처리할 PENDING 상태의 요청을 10개 가져옵니다.
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
                List<ForbiddenWord> forbiddenWordsList = forbiddenWordBatchRepository.findActiveForbiddenWords(searchParam);
                String forbiddenWords = forbiddenWordsList.stream()
                        .map(ForbiddenWord::getWord)
                        .collect(Collectors.joining(","));

                if(forbiddenWords.length() <= 0) {
                	// 3-3. 금칙어가 없는 경우 정상종료처리
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("COMPLETED");
                	request.setErrorMessage(null);
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "COMPLETED", "금칙어가 없습니다.");
                	return;
                }
                
                // 3-3. Gemini API 호출
                InspectionResult result = inspectService.performAiInspection(goods, filesToInspect, forbiddenWords);
                log.info("Gemini API 호출 결과: 승인여부 = {}, 사유 = {}", result.isApproved(), result.getReason());
                
                // 3-4. 결과에 따라 DB 상태를 업데이트합니다.
                if (result.isApproved()) {
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("COMPLETED");
                	request.setErrorMessage(null);
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "COMPLETED", null);
                } else {
                	request.setStatus("COMPLETED");
                	request.setInspectionStatus("FAILED");
                	request.setErrorMessage(String.format("코드 : %d, 사유 : %s", result.getErrorCode(), result.getReason()));
                	goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "COMPLETED", "FAILED", String.format("코드 : %d, 사유 : %s", result.getErrorCode(), result.getReason()));
                }

            } catch (Exception e) {
                int currentRetries = request.getRetries();
                
                // 재시도 횟수가 최대 횟수 미만인 경우
                if (currentRetries < MAX_RETRIES) {
                    log.info("!! request_id: {} 처리 중 오류 발생. 재시도를 위해 상태를 PENDING으로 변경합니다. (시도: {}) !!", 
                             request.getRequestId(), currentRetries + 1, e);
                    // 재시도 횟수를 1 증가시키고 상태를 다시 PENDING으로 업데이트합니다.
                    goodsBatchRequestRepository.incrementRetryCount(request.getRequestId());
                } else {
                    // 최대 재시도 횟수를 초과한 경우
                    log.error("!! request_id: {} 처리 중 심각한 오류 발생 (재시도 횟수 초과: {}) !!", 
                              request.getRequestId(), MAX_RETRIES, e);
                    // 최종적으로 FAILED 상태로 업데이트합니다.
                    goodsBatchRequestRepository.updateFinalStatus(request.getRequestId(), "FAILED", "FAILED", e.getMessage());
                }                
                
            } finally {
                log.info("--- request_id: {} 검수 처리 종료 ---", request.getRequestId());
            }
        }
        
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
            log.info("3. WebClient를 사용하여 콜백 URL로 결과 전송 시작. URL: {}", callbackUrl);
            WebClient webClient = webClientBuilder.build();

            webClient.post()
                     .uri(callbackUrl)
                     .contentType(MediaType.APPLICATION_JSON)
                     .body(Mono.just(jsonPayload), String.class)
                     .retrieve()
                     .bodyToMono(String.class)
                     .doOnSubscribe(subscription -> log.info("   - 콜백 API에 대한 비동기 요청 구독 시작..."))
                     .doOnSuccess(response ->
                        log.info("   - ✅ {}건의 배치 결과 전송 성공. 서버 응답: {}", completedRequests.size(), response))
                     .doOnError(error ->
                        log.error("   - ❌ !! 배치 결과 전송 실패 !! 원인: {}", error.getMessage(), error))
                     .doFinally(signalType ->
                        log.info("   - 비동기 요청 스트림 종료. Signal: {}", signalType))
                     .subscribe();

        } catch (Exception e) {
            log.error("!! WebClient 요청 설정 또는 실행 중 예외 발생 !!", e);
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
                    .map(path -> downloadUrl + path.trim()) // 각 경로 앞에 URL 붙이기 및 공백 제거
                    .collect(Collectors.toList());
            
            MultipartFile[] downloadedFiles = imageDownloadService.downloadImagesAsMultipartFiles(imageUrlList);
            
            // 1-2. 분할된 이미지를 FileContent로 변환
            for (MultipartFile file : downloadedFiles) {
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
                fileContents.add(new FileContent(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            }
        }

        return fileContents;        
    }

    /**
     * Csv파일을 파싱하는 메서드
     */
    private List<GoodsBatchDto> parseCsvToDto(File csvFile) throws IOException {
        // 1. CSV 표준(RFC 4180)을 따르는 파서를 생성합니다.
        RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();

        // 2. 파일을 읽습니다.
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(csvFile))) {

            // 3. 위에서 만든 표준 파서를 사용하여 CSV 리더(Reader)를 생성합니다.
            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(rfc4180Parser)
                    .build();

            // 4. DTO의 헤더 이름과 CSV의 헤더를 매핑하는 전략을 설정합니다.
            HeaderColumnNameMappingStrategy<GoodsBatchDto> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(GoodsBatchDto.class);

            // 5. CsvToBeanBuilder에 직접 만든 CSV 리더와 매핑 전략을 제공하여 최종 변환합니다.
            return new CsvToBeanBuilder<GoodsBatchDto>(csvReader)
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

    private File findCsvFile(Path dir) throws FileNotFoundException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path path : stream) {
                return path.toFile();
            }
        } catch (IOException e) {
            throw new FileNotFoundException("CSV 파일을 찾는 중 오류가 발생했습니다.");
        }
        throw new FileNotFoundException("디렉토리에서 CSV 파일을 찾을 수 없습니다: " + dir);
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

	public boolean gatherS3Data(String todayDate) {
        log.info("1. S3 버킷 '{}'의 '{}' 폴더에서 객체 목록 조회를 시작합니다.", s3BucketName, s3FolderName);

        // 객체 목록을 조회합니다.
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(s3BucketName)
                .withPrefix(s3FolderName);
        ObjectListing objectListing = amazonS3.listObjects(listObjectsRequest);
        List<S3ObjectSummary> s3Objects = objectListing.getObjectSummaries();

        log.info("   - {}개의 객체를 S3 버킷의 '{}' 폴더에서 발견했습니다.", s3Objects.size(), s3FolderName);

        if (s3Objects.isEmpty()) {
            log.info("2. 처리할 파일이 S3 폴더에 없습니다. 스케줄러를 종료합니다.");
            return true;
        }

        int zipFileCount = 0;
        for (S3ObjectSummary s3Object : s3Objects) {
            String key = s3Object.getKey();
            log.info("3. 객체 확인 중: '{}' (크기: {} bytes)", key, s3Object.getSize());

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
            
            zipFileCount++;
            log.info("   - '{}'은(는) ZIP 파일입니다. 다운로드를 시작합니다.", key);
            try {
                GetObjectRequest getObjectRequest = new GetObjectRequest(s3BucketName, key);
                com.amazonaws.services.s3.model.S3Object object = amazonS3.getObject(getObjectRequest);
                byte[] data = IOUtils.toByteArray(object.getObjectContent());
                log.info("   - '{}' 파일 다운로드 완료 ({} bytes).", key, data.length);

                log.info("4. 다운로드한 파일을 MultipartFile 객체로 변환합니다.");
                MultipartFile multipartFile = new UrlMultipartFile(data, key, "application/zip");
                log.info("   - MultipartFile 객체 생성 완료: '{}'", multipartFile.getOriginalFilename());

                log.info("5. GoodsBatchService의 processGoodsInspectionBatch 메서드를 호출하여 상품 검수 배치를 시작합니다.");
                boolean batchResult = processGoodsInspectionBatch(multipartFile);
                if(!batchResult) {
                	return false;
                }
                log.info("   - '{}' 파일에 대한 배치 처리 요청이 성공적으로 전달되었습니다.", key);

            } catch (Exception e) {
                log.error("!! '{}' 파일 처리 중 오류가 발생했습니다. 다음 파일로 넘어갑니다.", key, e);
            }
        }
        log.info("6. 총 {}개의 ZIP 파일을 처리했습니다.", zipFileCount);
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

}