package com.tikitaka.api.goods;

import lombok.extern.slf4j.Slf4j; // 로깅을 위한 Lombok Slf4j 어노테이션 임포트
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위한 Autowired 임포트
import org.springframework.http.HttpStatus; // HTTP 상태 코드를 위한 HttpStatus 임포트
import org.springframework.http.ResponseEntity; // HTTP 응답을 위한 ResponseEntity 임포트
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*; // REST 컨트롤러 관련 어노테이션 임포트
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.files.FilesService;
import com.tikitaka.api.files.FilesServiceImpl;
import com.tikitaka.api.files.entity.Files;
import com.tikitaka.api.global.dto.ApiResponseDto;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.image.ImageDownloadService;
import com.tikitaka.api.image.ImagePathExtractor;
import com.tikitaka.api.image.ImageSplittingService;
import com.tikitaka.api.inspection.InspectService;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.member.dto.CustomUserDetails;

import ch.qos.logback.core.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List; // List 인터페이스 임포트

@Slf4j // 로깅을 위한 Lombok 어노테이션 (클래스에 Logger 객체를 자동으로 생성)
@RestController // 이 클래스가 RESTful 웹 서비스의 컨트롤러임을 나타냅니다.
@RequestMapping("/goods") // 이 컨트롤러의 모든 핸들러 메서드에 대한 기본 URL 경로를 설정합니다.
public class GoodsController {

    private final FilesServiceImpl filesServiceImpl;

    private final SecurityFilterChain filterChain;
	private final GoodsService goodsService;
	private final FilesService filesService;
	private final InspectService inspectService;
	private final ImageSplittingService imageSplittingService;
	private final ImageDownloadService imageDownloadService;
	
	GoodsController(SecurityFilterChain filterChain, GoodsService goodsService, FilesService filesService, InspectService inspectService, ImageSplittingService imageSplittingService, ImageDownloadService imageDownloadService, FilesServiceImpl filesServiceImpl) {
        this.filterChain = filterChain;
        this.goodsService = goodsService;
        this.filesService = filesService;
        this.inspectService = inspectService;
        this.imageSplittingService = imageSplittingService;
        this.imageDownloadService = imageDownloadService;
        this.filesServiceImpl = filesServiceImpl;
    }

	/**
	 * 모든 상품 목록을 조회하는 API 엔드포인트.
	 * HTTP GET 요청을 처리하며, "/goods/list" 경로에 매핑됩니다.
	 *
	 * @return ResponseEntity<ApiResponseDto<List<Goods>>> 상품 목록과 API 응답 결과.
	 * 성공 시 HTTP 200 OK와 상품 목록을 반환합니다.
	 */
	@GetMapping("/list")
    public ResponseEntity<ApiResponseDto<List<GoodsListDto>>> getGoodsList() {
        log.info("상품 목록 조회 요청이 들어왔습니다."); // 로그 메시지 기록
        // GoodsService를 통해 모든 상품을 조회합니다.
        List<GoodsListDto> goodsList = goodsService.findAllbyPeriodWithFiles();
        
        // 성공 응답 (HTTP 200 OK)과 함께 상품 목록 데이터를 ApiResponseDto.success()로 래핑하여 반환합니다.
        return ResponseEntity.ok(ApiResponseDto.success("상품 목록을 성공적으로 조회했습니다.", goodsList));
	}


	/**
	 * 모든 상품 목록을 조회하는 API 엔드포인트.
	 * HTTP GET 요청을 처리하며, "/goods/list" 경로에 매핑됩니다.
	 *
	 * @return ResponseEntity<ApiResponseDto<List<Goods>>> 상품 목록과 API 응답 결과.
	 * 성공 시 HTTP 200 OK와 상품 목록을 반환합니다.
	 */
	@GetMapping("/{goodsId}")
    public ResponseEntity<ApiResponseDto<GoodsListDto>> getGoods(@PathVariable Long goodsId) {
        log.info("상품 상세 조회 요청이 들어왔습니다."); // 로그 메시지 기록
        // GoodsService를 통해 모든 상품을 조회합니다.
        GoodsListDto goodsDto = goodsService.findbyPeriodWithFiles(goodsId);
        
        // 성공 응답 (HTTP 200 OK)과 함께 상품 목록 데이터를 ApiResponseDto.success()로 래핑하여 반환합니다.
        return ResponseEntity.ok(ApiResponseDto.success("상품 목록을 성공적으로 조회했습니다.", goodsDto));
	}
	
	/**
     * 상품 정보(JSON)와 이미지 파일(multipart)을 함께 받아 상품을 검수합니다.
     */
    @PostMapping("/inspect")
    public ResponseEntity<?> inspectGoodsWithPhoto(
    		@RequestPart(name = "representativeFile", required = false) MultipartFile[] representativeFile,
            @RequestPart(name = "goodsId", required = false) String goodsId,
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart(name = "origin", required = false) String origin,
            @RequestPart(name="imageType", required = true) String imageType,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
            @RequestPart(name = "imageHtml", required = false) String imageHtml,
            @RequestPart(name = "isFileNew", required = false) String isFileNew,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("상품 검수 요청이 들어왔습니다: {}", goodsName);
        try {
            // 1. Goods 객체 기본 정보 생성
            Goods newGoods = new Goods(
                goodsName,
                mobileGoodsName,
                Long.valueOf(salesPriceStr),
                Long.valueOf(buyPriceStr),
                StringUtil.isNullOrEmpty(origin) ? "국내산" : origin,
                userDetails.getMemberId(),
                userDetails.getMemberId()
            );

            InspectionResult result = null;

            // 2. 로직 분기: (신규 상품 등록)
            if ("true".equals(isFileNew)) {
                log.info("신규 상품 검수를 시작합니다.");
                
                MultipartFile[] filesToInspect;
                
                // html 태그를 받은 경우
                if ("html".equals(imageType)) {
                    List<String> imageUrlList = ImagePathExtractor.extractImageUrls(imageHtml);
                    // 외부 이미지를 다운로드
                    MultipartFile[] downloadedImageFiles = this.imageDownloadService.downloadImagesAsMultipartFiles(imageUrlList);
                    // 다운로드한 파일과 대표 파일을 합칩니다.
                    filesToInspect = combineMultipartFiles(downloadedImageFiles, representativeFile);
                } else { // 파일을 받은 경우
                	
                    MultipartFile[] splittedImageFiles = this.imageSplittingService.splitImages(imageFiles, 1600);
                    // 분할된 파일과 대표 파일을 합칩니다.
                    filesToInspect = combineMultipartFiles(splittedImageFiles, representativeFile);
                }
                // 신규 상품은 항상 새로운 파일을 사용해야 합니다.
                result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect);

            // 상품 수정 화면
            } else {
                if (StringUtil.isNullOrEmpty(goodsId)) {
                    throw new IllegalArgumentException("상품 ID가 누락되었습니다.");
                }
                
                List<Files> dbRepresentativeFile = new ArrayList<>();
                if(representativeFile == null || representativeFile.length <= 0) {
                	List<Files> dbFiles = this.filesService.findByGoodsId(Long.valueOf(goodsId));
                	for(Files dbFile: dbFiles) {
                		if(dbFile.isRepresentativeYn()) {
                			dbRepresentativeFile.add(dbFile);
                			break;
                		}
                	}
                }
                newGoods.setGoodsId(Long.valueOf(goodsId));
                log.info("기존 상품(ID: {}) 수정을 위한 검수를 시작합니다.", goodsId);

                // 파일 내용 검수를 위한 MultipartFile 배열 또는 FileContent 리스트를 준비합니다.
                MultipartFile[] filesToInspect = null;
                List<FileContent> filesToInspect2 = null;
                
                // 상세 이미지가 HTML 방식인 경우
                if ("html".equals(imageType)) {
                	// 새로운 HTML 내용이 있는 경우
                	if (!StringUtil.isNullOrEmpty(imageHtml)) {
                		log.info("새로운 HTML 내용으로 검수를 진행합니다.");
                        List<String> imageUrlList = ImagePathExtractor.extractImageUrls(imageHtml);
                        MultipartFile[] downloadedImageFiles = this.imageDownloadService.downloadImagesAsMultipartFiles(imageUrlList);
                        
                        if(representativeFile == null || representativeFile.length <= 0) {
                        	List<FileContent> dbFiles = this.filesService.readFiles(dbRepresentativeFile);
                        	filesToInspect2 = combineFileContentWithMultipartFile(dbFiles, downloadedImageFiles);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);
                        } else {
                            filesToInspect = combineMultipartFiles(downloadedImageFiles, representativeFile);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect);
                        }
                	} else {
                		// 새로운 HTML 내용이 없는 경우, 기존 DB 파일을 사용
                		log.info("기존 저장된 파일로 검수를 진행합니다.");
                		List<Files> savedFiles = this.filesService.findByGoodsId(newGoods.getGoodsId());
                		List<FileContent> fileContentListToInspect = this.filesService.readFiles(savedFiles);

                        if(representativeFile == null || representativeFile.length <= 0) {
                        	List<FileContent> dbFiles = this.filesService.readFiles(dbRepresentativeFile);
                        	filesToInspect2 = combineFileContent(dbFiles, fileContentListToInspect);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);
                        } else {
                            filesToInspect2 = combineFileContentWithMultipartFile(fileContentListToInspect, representativeFile);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);                        	
                        }
                	}

                // 상세 이미지가 파일 첨부 방식인 경우
                } else if ("file".equals(imageType)) {
                    // 새로운 파일이 첨부된 경우
                    if (imageFiles != null && imageFiles.length > 0 && !imageFiles[0].isEmpty()) {
                        log.info("새로운 첨부 파일로 검수를 진행합니다.");
                        
                        if(representativeFile == null || representativeFile.length <= 0) {
                        	List<FileContent> dbFiles = this.filesService.readFiles(dbRepresentativeFile);
                            MultipartFile[] splittedImageFiles = this.imageSplittingService.splitImages(imageFiles, 1600);
                        	filesToInspect2 = combineFileContentWithMultipartFile(dbFiles, splittedImageFiles);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);
                        	
                        } else {
                            MultipartFile[] splittedImageFiles = this.imageSplittingService.splitImages(imageFiles, 1600);
                            filesToInspect = combineMultipartFiles(splittedImageFiles, representativeFile);
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect);                        	
                        }
                        
                    } else {
                        // 파일이 새로 첨부되지 않은 경우, 기존 DB 파일을 사용
                        log.info("기존 저장된 파일로 검수를 진행합니다.");

                        if(representativeFile == null || representativeFile.length <= 0) {
                        	List<FileContent> dbFiles = this.filesService.readFiles(dbRepresentativeFile);

                        	List<Files> savedFiles = this.filesService.findByGoodsId(newGoods.getGoodsId());
                            List<FileContent> fileContentListToInspect = this.filesService.readFiles(savedFiles);
                            
                        	filesToInspect2 = combineFileContent(dbFiles, fileContentListToInspect);
                            
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);

                        	
                        } else {
                            List<Files> savedFiles = this.filesService.findByGoodsId(newGoods.getGoodsId());
                            List<FileContent> fileContentListToInspect = this.filesService.readFiles(savedFiles);
                            
                            filesToInspect2 = combineFileContentWithMultipartFile(fileContentListToInspect, representativeFile);
                            
                            result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, filesToInspect2);                        	
                        }
                    }
                }
            }

            System.out.println("result.isApproved() : " + result.isApproved());
            System.out.println("newGoods.getGoodsId() : " + newGoods.getGoodsId());

            // 승인 + 수정인 경우 (goodsId가 null이 아님)
            if (result.isApproved() && newGoods.getGoodsId() != null) {
                newGoods.setAiCheckYn("Y");
                this.goodsService.updateAiCheckYn(newGoods);
            }

            // 성공 응답 반환
            return ResponseEntity.ok(ApiResponseDto.success("상품을 성공적으로 검수했습니다.", result));
        } catch (Exception e) {
            // 실패 응답 반환
            log.error("상품 검수 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(ApiResponseDto.fail("상품 검수에 실패했습니다: " + e.getMessage()));
        }
    }
	
    /**
     * 두 개의 MultipartFile 배열을 하나로 합치는 헬퍼 메서드
     * @param files1 첫 번째 파일 배열
     * @param files2 두 번째 파일 배열
     * @return 합쳐진 파일 배열
     */
    private MultipartFile[] combineMultipartFiles(MultipartFile[] files1, MultipartFile[] files2) {
        List<MultipartFile> combinedList = new ArrayList<>();
        if (files1 != null) {
            Collections.addAll(combinedList, files1);
        }
        if (files2 != null) {
            Collections.addAll(combinedList, files2);
        }
        return combinedList.toArray(new MultipartFile[0]);
    }
    
    /**
     * FileContent 리스트와 MultipartFile 배열을 하나로 합치는 헬퍼 메서드
     * @param files1 첫 번째 FileContent 파일 리스트
     * @param files2 두 번째 Multipart파일 배열
     * @return 합쳐진 파일 배열
     */
    private List<FileContent> combineFileContentWithMultipartFile(List<FileContent> files1, MultipartFile[] files2) {
        List<FileContent> combinedList = new ArrayList<>();

        // 첫 번째 파일 리스트가 null이 아니면 추가
        if (files1 != null) {
            combinedList.addAll(files1);
        }

        // 두 번째 파일 배열이 null이 아니고 비어있지 않으면 추가
        if (files2 != null && files2.length > 0) {
            for (MultipartFile multipartFile : files2) {
                // MultipartFile을 FileContent로 변환하여 리스트에 추가
                try {
                    String originalFilename = multipartFile.getOriginalFilename();
                    String mimeType = multipartFile.getContentType();
                    byte[] content = multipartFile.getBytes();

                    FileContent fileContent = new FileContent(originalFilename, mimeType, content);
                    combinedList.add(fileContent);
                } catch (IOException e) {
                    // 파일 읽기 중 오류가 발생하면 예외 처리
                    System.err.println("파일 변환 중 오류 발생: " + e.getMessage());
                    // 필요에 따라 적절한 예외 처리 로직 추가 (예: 예외를 다시 던지거나, 로그를 남기거나)
                }
            }
        }

        return combinedList;
    }
    /**
     * FileContent 리스트와 FileContent 리스트와 하나로 합치는 헬퍼 메서드
     * @param files1 첫 번째 FileContent 파일 리스트
     * @param files2 두 번째 FileContent 파일 배열
     * @return 합쳐진 파일 배열
     */
    private List<FileContent> combineFileContent(List<FileContent> files1, List<FileContent> files2) {
        List<FileContent> combinedList = new ArrayList<>();

        // 첫 번째 파일 리스트가 null이 아니면 추가
        if (files1 != null) {
            combinedList.addAll(files1);
        }

        // 두 번째 파일 배열이 null이 아니고 비어있지 않으면 추가
        if (files2 != null && files2.size() > 0) {
            combinedList.addAll(files2);
        }

        return combinedList;
    }    
    
	/**
     * 상품 정보(JSON)와 이미지 파일(multipart)을 함께 받아 상품을 등록합니다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerGoodsWithPhoto(
    		@RequestPart("representativeFile") MultipartFile[] representativeFile,
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart(name = "origin", required = false) String origin,
            @RequestPart(name="imageType", required = true) String imageType,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
            @RequestPart(name = "imageHtml", required = false) String imageHtml,
            @RequestPart(name = "aiCheckYn", required = false) String aiCheckYn,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("상품 등록 요청이 들어왔습니다: {}", goodsName);

        try {
            // 1. DTO나 Entity를 먼저 생성합니다.
            Goods newGoods = new Goods(
                goodsName, 
                mobileGoodsName, 
                Long.valueOf(salesPriceStr), 
                Long.valueOf(buyPriceStr),
                StringUtil.isNullOrEmpty(origin) ? "국내산" : origin,
                userDetails.getMemberId(), 
                userDetails.getMemberId()
            );
            newGoods.setAiCheckYn(StringUtil.isNullOrEmpty(aiCheckYn) ? "N" : aiCheckYn);
            
            // 2. 확보된 상품 정보와 파일을 FilesService로 전달하여 파일 처리
            Goods savedGoods = goodsService.save(newGoods);

            // 대표사진부터 저장
            filesService.save(savedGoods, representativeFile, userDetails, true);
            
            if(imageType.equals("html")) {
	            // 신규 상품은 항상 새로운 파일을 사용해야 합니다.
	            filesService.save(savedGoods, imageHtml, userDetails);
            } else {
                MultipartFile[] splittedImageFiles = this.imageSplittingService.splitImages(imageFiles, 1600);
                filesService.save(savedGoods, splittedImageFiles, userDetails);      
            }
            
            // 성공 응답 반환
            return ResponseEntity.status(HttpStatus.CREATED)
                                 .body(ApiResponseDto.success("상품이 성공적으로 등록되었습니다.", savedGoods));

        } catch (Exception e) {
            log.error("상품 등록 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(ApiResponseDto.fail("상품 등록에 실패했습니다: " + e.getMessage()));
        }
    }
    

    /**
     * [핵심 수정] 상품 정보를 업데이트하는 API 엔드포인트.
     * multipart/form-data 형식의 PUT 요청을 처리합니다.
     */
    @PutMapping("/update")
    public ResponseEntity<?> updateGoodsWithPhoto(
    		@RequestPart(name = "representativeFile", required = false) MultipartFile[] representativeFile,
            @RequestPart("goodsId") String goodsIdStr,
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart("origin") String origin,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
            @RequestPart(name="imageType", required = true) String imageType,
            @RequestPart(name = "imageHtml", required = false) String imageHtml,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("상품 업데이트 요청이 들어왔습니다. ID: {}", goodsIdStr);
        try {
            Goods goodsToUpdate = new Goods(
                Long.valueOf(goodsIdStr),
                goodsName,
                mobileGoodsName,
                Long.valueOf(salesPriceStr),
                Long.valueOf(buyPriceStr),
                origin,
                userDetails.getMemberId(), // insertId는 그대로 두고 updateId만 변경
                userDetails.getMemberId()
            );
            
            boolean isUpdated = false;
            if(imageType.equals("html")) {
                log.info("html임. imageHtml: {}", imageHtml);
                // 서비스 레이어에 모든 정보를 전달하여 업데이트 로직을 위임
                isUpdated = goodsService.updateWithFiles(goodsToUpdate, representativeFile, imageHtml, userDetails);            	
            } else {
                log.info("file임. imageFiles: {}", imageFiles);
                // 서비스 레이어에 모든 정보를 전달하여 업데이트 로직을 위임
                isUpdated = goodsService.updateWithFiles(goodsToUpdate, representativeFile, imageFiles, userDetails);
            }

            if (isUpdated) {
                return ResponseEntity.ok(ApiResponseDto.success("상품이 성공적으로 업데이트되었습니다.", true));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                     .body(ApiResponseDto.fail("상품 업데이트에 실패했습니다. 해당 상품을 찾을 수 없습니다."));
            }
        } catch (Exception e) {
            log.error("상품 업데이트 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(ApiResponseDto.fail("상품 업데이트 중 서버 오류가 발생했습니다."));
        }
    }

    /**
     * 특정 ID의 상품을 삭제하는 API 엔드포인트.
     * HTTP DELETE 요청을 처리하며, "/goods/{goodsId}" 경로에 매핑됩니다.
     *
     * @param goodsId 삭제할 상품의 ID (URL 경로 변수로 받음).
     * @return ResponseEntity<ApiResponseDto<Boolean>> 삭제 성공 여부와 API 응답 결과.
     * 성공 시 HTTP 200 OK, 실패 시 HTTP 404 NOT_FOUND를 반환합니다.
     */
    @DeleteMapping("/{goodsId}")
    public ResponseEntity<?> deleteGoods(@PathVariable Long goodsId) {
        log.info("상품 삭제 요청이 들어왔습니다. ID: {}", goodsId); // 로그 메시지 기록
        // GoodsService를 통해 상품을 삭제합니다.
        boolean isDeleted = goodsService.delete(goodsId);

        // 삭제 성공 여부에 따라 응답을 다르게 처리합니다.
        if (isDeleted) {
            // 성공 응답 (HTTP 200 OK)과 함께 삭제 성공 여부 (true)를 ApiResponseDto.success()로 반환합니다.
            return ResponseEntity.ok(ApiResponseDto.success("상품이 성공적으로 삭제되었습니다.", true));
        } else {
            // 삭제 실패 (예: 해당 ID의 상품이 없거나 DB 오류) 시 (HTTP 404 Not Found) 에러 메시지를 ApiResponseDto.fail()로 반환합니다.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                 .body(ApiResponseDto.fail("상품 삭제에 실패했습니다. 해당 상품을 찾을 수 없습니다."));
        }
    }
}
