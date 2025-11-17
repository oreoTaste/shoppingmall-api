package com.tikitaka.api.batch.goods;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.batch.inspection.dto.InspectionResultReq;
import com.tikitaka.api.global.dto.ApiResponseDto;
import com.tikitaka.api.goods.dto.GoodsListDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/goods-batch")
@RequiredArgsConstructor
public class GoodsBatchController {

	private final GoodsBatchService goodsBatchService;
	private final BatchInspectionScheduler batchInspectionScheduler;
	
//    /**
//     * 상품 정보와 이미지를 받아 검수합니다.
//     *
//     * @param request     검수 요청 정보 (DTO)
//     * @param userDetails 인증된 사용자 정보
//     * @return 검수 결과
//     */
//    @PostMapping("/inspect")
//    public ResponseEntity<String> inspectGoodsBatch(@RequestParam("file") MultipartFile zipFile) {
//        if (zipFile.isEmpty() || !zipFile.getOriginalFilename().toLowerCase().endsWith(".zip")) {
//            return ResponseEntity.badRequest().body("ZIP 파일만 업로드할 수 있습니다.");
//        }
//        
//        goodsBatchService.processGoodsInspectionBatch(zipFile);
//        
//        try {
//        	goodsBatchService.processPendingBatchRequests(100);        	
//        } catch(Exception e) {
//            log.info("입력 시 배치 자동 수행");
//        }
//        return ResponseEntity.ok("배치 처리 요청이 성공적으로 접수되었습니다. 처리 완료 후 알림을 확인하세요.");
//    }
	
    @GetMapping("/inspect")
    public ResponseEntity<String> triggerGoodsBatch() {
    	batchInspectionScheduler.gatherS3Data();
        return ResponseEntity.ok("배치 처리 요청이 성공적으로 접수되었습니다. 처리 완료 후 알림을 확인하세요.");
        
    }

    @GetMapping("/get-results")
    public ResponseEntity<ApiResponseDto<?>> getResults(InspectionResultReq inspectionResultReq) {
        return ResponseEntity.ok(ApiResponseDto.success("상품 목록을 성공적으로 조회했습니다.", goodsBatchService.getGoodsBatchResult(inspectionResultReq)));
        
    }

}
