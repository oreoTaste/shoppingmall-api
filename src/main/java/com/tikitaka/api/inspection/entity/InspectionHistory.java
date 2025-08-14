package com.tikitaka.api.inspection.entity;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.member.dto.CustomUserDetails;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Getter
@ToString
@Builder // 빌더 패턴을 사용하여 객체 생성을 더 명확하게 합니다.
public class InspectionHistory {
    private Long inspectionId;
    private Long goodsId;
    private ZonedDateTime inspectionDate; // ZonedDateTime으로 변경
    private String isApproved;
    private int errorCode;
    private String reason;
    private String inspectorId;

    /**
     * 상품, 검수결과, 사용자 정보를 바탕으로 History 엔티티를 생성하는 정적 메서드
     */
    public static InspectionHistory of(Goods goods, InspectionResult result, CustomUserDetails user) {
        return InspectionHistory.builder()
                .goodsId(goods.getGoodsId())
                .inspectionDate(ZonedDateTime.now())
                .isApproved(result.isApproved() ? "Y" : "N")
                .errorCode(result.getErrorCode())
                .reason(result.getReason())
                .inspectorId(result.getInspectorId())
                .build();
    }
}