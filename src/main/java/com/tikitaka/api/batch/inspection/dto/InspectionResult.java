package com.tikitaka.api.batch.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionResult {

    private boolean approved; // 검수 통과 여부
    private int errorCode;		// 반려 코드
    private String reason;    // 반려 사유
    private String forbiddenWord;
    
    private String inspectorId;	// 검수엔진 (20자리, gpt-4o, gemini-2.0-flash) 

//    public static InspectionResult approve(String inspectorId) {
//        return new InspectionResult(true, "승인되었습니다.", null, inspectorId);
//    }
//
//    public static InspectionResult reject(String forbiddenWord, String reason, String inspectorId) {
//        return new InspectionResult(false, reason, forbiddenWord, inspectorId);
//    }
    
    
    public static InspectionResult approve(String inspectorId) {
        return new InspectionResult(true, 0, "승인되었습니다.", "", inspectorId);
    }

    public static InspectionResult reject(int errorCode, String forbiddenWord, String reason, String inspectorId) {
        return new InspectionResult(false, errorCode, reason, forbiddenWord, inspectorId);
    }
}
