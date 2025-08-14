package com.tikitaka.api.inspection;

import com.tikitaka.api.inspection.entity.InspectionHistory;

public interface InspectionHistoryRepository {
    /**
     * 검수 결과 내역을 저장합니다.
     * @param history 저장할 InspectionHistory 객체
     * @return 저장된 InspectionHistory 객체 (ID 포함)
     */
    InspectionHistory save(InspectionHistory history);
}