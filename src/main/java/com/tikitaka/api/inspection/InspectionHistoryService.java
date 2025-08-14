package com.tikitaka.api.inspection;


import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.inspection.entity.InspectionHistory;
import com.tikitaka.api.member.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InspectionHistoryService {

    private final InspectionHistoryRepository inspectionHistoryRepository;

    /**
     * 검수 결과를 받아서 History DB에 저장합니다.
     * @param goods 검수한 상품 정보
     * @param result AI 검수 결과
     * @param user 검수를 요청한 사용자 정보
     */
    public void recordInspectionHistory(Goods goods, InspectionResult result, CustomUserDetails user) {
        try {
            // 팩토리 메서드를 사용하여 History 객체 생성
            InspectionHistory history = InspectionHistory.of(goods, result, user);
            inspectionHistoryRepository.save(history);
            log.info("상품 ID {}에 대한 검수 결과가 저장되었습니다.", goods.getGoodsId());
        } catch (Exception e) {
            log.error("검수 결과 저장 중 오류 발생. 상품ID: {}, 결과: {}", goods.getGoodsId(), result, e);
            // 필요 시 예외를 다시 던져서 트랜잭션을 롤백할 수 있습니다.
            // throw new RuntimeException("검수 결과 저장에 실패했습니다.", e);
        }
    }
}