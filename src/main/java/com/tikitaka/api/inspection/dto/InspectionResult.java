package com.tikitaka.api.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionResult {

    private boolean approved; // 검수 통과 여부
    private String reason;    // 반려 사유

    public static InspectionResult approve() {
        return new InspectionResult(true, "승인되었습니다.");
    }

    public static InspectionResult reject(String reason) {
        return new InspectionResult(false, reason);
    }
}
