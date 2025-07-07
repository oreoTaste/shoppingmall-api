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
import com.tikitaka.api.files.entity.Files;
import com.tikitaka.api.global.dto.ApiResponseDto;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.InspectService;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.member.dto.CustomUserDetails;

import ch.qos.logback.core.util.StringUtil;

import java.util.List; // List 인터페이스 임포트

@Slf4j // 로깅을 위한 Lombok 어노테이션 (클래스에 Logger 객체를 자동으로 생성)
@RestController // 이 클래스가 RESTful 웹 서비스의 컨트롤러임을 나타냅니다.
@RequestMapping("/goods") // 이 컨트롤러의 모든 핸들러 메서드에 대한 기본 URL 경로를 설정합니다.
public class GoodsController {

    private final SecurityFilterChain filterChain;

	// GoodsService를 자동으로 주입받습니다.
	// Spring 컨테이너가 GoodsService 타입의 빈을 찾아 이 필드에 할당합니다.
	@Autowired
	private GoodsService goodsService;
	@Autowired
	private FilesService filesService;
	@Autowired
	private InspectService inspectService;

    GoodsController(SecurityFilterChain filterChain) {
        this.filterChain = filterChain;
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
            @RequestPart(name = "goodsId", required = false) String goodsId,
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart(name = "origin", required = false) String origin,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
            @RequestPart(name = "isFileNew", required = false) String isFileNew,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("상품 검수 요청이 들어왔습니다: {}", goodsName);
        InspectionResult result;
        try {
            Goods newGoods = new Goods(
                goodsName, 
                mobileGoodsName, 
                Long.valueOf(salesPriceStr), 
                Long.valueOf(buyPriceStr), 
                StringUtil.isNullOrEmpty(origin) ? "국내산" : origin,
                userDetails.getMemberId(), 
                userDetails.getMemberId()
            );
            
            // to-do) 수정화면에서 inspection할때만 오류가 나는걸로 봐서 이부분에서 오류가 나는걸로 추정
            // 첨부파일이 없는 경우, 기존에 저장된 첨부파일을 통해
            if("false".equals(isFileNew) && !goodsId.isBlank()) {
                // 1. DB에서 파일 정보 목록을 가져옵니다.
                List<Files> savedFiles = this.filesService.findByGoodsId(Long.valueOf(goodsId));
                log.info("savedFiles");
                for(Files file: savedFiles) {
                    log.info(file.toString());                	
                }
                
                // 2. 실제 파일 내용을 읽어옵니다.
                List<FileContent> fileContent = this.filesService.readFiles(savedFiles);
                result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, fileContent);
            } else {
                result = this.inspectService.inspectGoodsInfoWithPhotos(newGoods, imageFiles);            	
            }
            
            // 성공 응답 반환
            return ResponseEntity.ok(ApiResponseDto.success("상품을 성공적으로 검수했습니다.", result));
        } catch(Exception e) {
            // 실패 응답 반환
            log.error("상품 검수 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body(ApiResponseDto.fail("상품 검수에 실패했습니다: " + e.getMessage()));
        }
    }
	
	/**
     * 상품 정보(JSON)와 이미지 파일(multipart)을 함께 받아 상품을 등록합니다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerGoodsWithPhoto(
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart(name = "origin", required = false) String origin,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
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
            filesService.save(savedGoods, imageFiles, userDetails);      
            
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
            @RequestPart("goodsId") String goodsIdStr,
            @RequestPart("goodsName") String goodsName,
            @RequestPart("mobileGoodsName") String mobileGoodsName,
            @RequestPart("salesPrice") String salesPriceStr,
            @RequestPart("buyPrice") String buyPriceStr,
            @RequestPart("origin") String origin,
            @RequestPart(name = "files", required = false) MultipartFile[] imageFiles,
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

            // 서비스 레이어에 모든 정보를 전달하여 업데이트 로직을 위임
            boolean isUpdated = goodsService.updateWithFiles(goodsToUpdate, imageFiles, userDetails);

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
