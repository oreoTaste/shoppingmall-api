package com.tikitaka.api.batch.goods.dto;

import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchResultPayload {
    private Long requestId;
    private String batchJobId;
    private String goodsCode;
    private String status;
    private String inspectionStatus;
    private String errorMessage;

    public static BatchResultPayload from(GoodsBatchRequest request) {
        return BatchResultPayload.builder()
                .requestId(request.getRequestId())
                .batchJobId(request.getBatchJobId())
                .goodsCode(request.getGoodsCode())
                .status(request.getStatus())
                .inspectionStatus(request.getInspectionStatus())
                .errorMessage(request.getErrorMessage())
                .build();
    }
}