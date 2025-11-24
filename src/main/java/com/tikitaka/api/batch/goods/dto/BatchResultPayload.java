package com.tikitaka.api.batch.goods.dto;

import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchResultPayload {
    private Long requestId;
    private String goodsCode;
    private String status;
    private String inspectionStatus;
    private String errorMessage;
    private String forbiddenWord;

    public static BatchResultPayload from(GoodsBatchRequest request) {
        return BatchResultPayload.builder()
                .requestId(request.getRequestId())
                .goodsCode(request.getGoodsCode())
                .status(request.getStatus())
                .inspectionStatus(request.getInspectionStatus())
                .forbiddenWord(request.getForbiddenWord())
                .errorMessage(request.getErrorMessage())
                .build();
    }
}