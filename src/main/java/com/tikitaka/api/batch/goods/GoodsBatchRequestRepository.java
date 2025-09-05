package com.tikitaka.api.batch.goods;

import java.util.List;

import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;

public interface GoodsBatchRequestRepository {
    /**
     * 여러 개의 상품 배치 요청을 한 번에 저장합니다.
     * @param requests 저장할 GoodsBatchRequest 객체 리스트
     */
    void saveAll(List<GoodsBatchRequest> requests);


    /**
     * 'PENDING' 상태의 요청을 지정된 개수만큼 조회합니다.
     * @param limit 조회할 최대 개수
     * @return GoodsBatchRequest 객체 리스트
     */
    List<GoodsBatchRequest> findPendingRequests(int limit);

    /**
     * 여러 요청의 상태를 한 번에 'PROCESSING'으로 변경합니다.
     * @param ids 상태를 변경할 요청 ID 리스트
     */
    void updateStatusToProcessing(List<Long> ids);

    /**
     * 단일 요청의 상태를 'COMPLETED' 또는 'FAILED'로 변경합니다.
     * @param requestId 요청 ID
     * @param status 변경할 최종 상태
     * @param errorMessage 실패 시 에러 메시지
     */
    void updateFinalStatus(Long requestId, String status, String inspectionStatus, String errorMessage);

    /**
     * 오래된 배치 기록을 조회합니다.
     * @param day 소요시간 (오늘로부터 day만큼 지난 데이터를 조회)
     */
    List<String> findOldBatchRecord(int day);
    
    /**
     * 오래된 배치 기록을 삭제합니다.
     * @param day 소요시간 (오늘로부터 day만큼 지난 데이터는 삭제처리)
     */
    void deleteOldBatchRecord(int day);

}