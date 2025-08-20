package com.tikitaka.api.goods;

import com.tikitaka.api.global.dto.ApiResponseDto;
import com.tikitaka.api.goods.dto.GoodsInspectRequestDto;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.dto.GoodsRegisterRequestDto;
import com.tikitaka.api.goods.dto.GoodsUpdateRequestDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.member.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /**
     * 모든 상품 목록을 조회합니다.
     *
     * @return 상품 목록과 API 응답 결과
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponseDto<List<GoodsListDto>>> getGoodsList() {
        log.info("상품 목록 조회 요청");
        List<GoodsListDto> goodsList = goodsService.findAllbyPeriodWithFiles();
        return ResponseEntity.ok(ApiResponseDto.success("상품 목록을 성공적으로 조회했습니다.", goodsList));
    }

    /**
     * 특정 상품의 상세 정보를 조회합니다.
     *
     * @param goodsId 조회할 상품의 ID
     * @return 상품 상세 정보와 API 응답 결과
     */
    @GetMapping("/{goodsId}")
    public ResponseEntity<ApiResponseDto<?>> getGoods(@PathVariable Long goodsId) {
        log.info("상품 상세 조회 요청: {}", goodsId);
        GoodsListDto goodsDto = goodsService.findbyPeriodWithFiles(goodsId);

        if (goodsDto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDto.fail("해당 ID의 상품을 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(ApiResponseDto.success("상품 상세 정보를 성공적으로 조회했습니다.", goodsDto));
    }

    /**
     * 상품 정보와 이미지를 받아 검수합니다.
     *
     * @param request     검수 요청 정보 (DTO)
     * @param userDetails 인증된 사용자 정보
     * @return 검수 결과
     */
    @PostMapping("/inspect")
    public ResponseEntity<ApiResponseDto<?>> inspectGoodsWithPhoto(
            @ModelAttribute GoodsInspectRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("상품 검수 요청: {}", request.getGoodsName());
        try {
            InspectionResult result = goodsService.inspectNewGoods(request, userDetails);
            return ResponseEntity.ok(ApiResponseDto.success("상품을 성공적으로 검수했습니다.", result));
        } catch (IllegalArgumentException e) {
            log.warn("상품 검수 중 유효성 검사 실패: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.fail(e.getMessage()));
        } catch (IOException e) {
            log.error("상품 검수 중 파일 처리 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.fail("상품 검수 중 파일 처리에 실패했습니다."));
        } catch (Exception e) {
            log.error("상품 검수 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.fail("상품 검수에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 상품 정보와 이미지 파일을 함께 받아 상품을 등록합니다.
     *
     * @param request     상품 등록 정보 (DTO)
     * @param userDetails 인증된 사용자 정보
     * @return 등록된 상품 정보
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponseDto<?>> registerGoods(
            @ModelAttribute GoodsRegisterRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("상품 등록 요청: {}", request.getGoodsName());
        try {
            Goods savedGoods = goodsService.registerGoods(request, userDetails);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponseDto.success("상품이 성공적으로 등록되었습니다.", savedGoods));
        } catch (Exception e) {
            log.error("상품 등록 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.fail("상품 등록에 실패했습니다: " + e.getMessage()));
        }
    }

    /**
     * 상품 정보를 업데이트합니다.
     *
     * @param request     상품 수정 정보 (DTO)
     * @param userDetails 인증된 사용자 정보
     * @return 업데이트 성공 여부
     */
    @PutMapping("/update")
    public ResponseEntity<ApiResponseDto<?>> updateGoods(
            @ModelAttribute GoodsUpdateRequestDto request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("상품 업데이트 요청: {}", request.getGoodsId());
        try {
            boolean isUpdated = goodsService.updateGoods(request, userDetails);
            if (isUpdated) {
                return ResponseEntity.ok(ApiResponseDto.success("상품이 성공적으로 업데이트되었습니다.", true));
            }
            // isUpdated가 false인 경우는 service 내부에서 예외를 던지거나, 해당 상품이 없는 경우입니다.
            // 여기서는 상품을 찾지 못한 경우로 통일하여 응답합니다.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDto.fail("상품 업데이트에 실패했습니다. 해당 상품을 찾을 수 없습니다."));
        } catch (IOException e) {
            log.error("상품 업데이트 중 파일 처리 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.fail("상품 업데이트 중 파일 처리에 실패했습니다."));
        } catch (Exception e) {
            log.error("상품 업데이트 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseDto.fail("상품 업데이트 중 서버 오류가 발생했습니다."));
        }
    }

    /**
     * 특정 ID의 상품을 삭제합니다.
     *
     * @param goodsId 삭제할 상품의 ID
     * @return 삭제 성공 여부
     */
    @DeleteMapping("/{goodsId}")
    public ResponseEntity<ApiResponseDto<?>> deleteGoods(@PathVariable Long goodsId) {
        log.info("상품 삭제 요청: {}", goodsId);
        boolean isDeleted = goodsService.delete(goodsId);

        if (isDeleted) {
            return ResponseEntity.ok(ApiResponseDto.success("상품이 성공적으로 삭제되었습니다.", true));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponseDto.fail("상품 삭제에 실패했습니다. 해당 상품을 찾을 수 없습니다."));
        }
    }
}