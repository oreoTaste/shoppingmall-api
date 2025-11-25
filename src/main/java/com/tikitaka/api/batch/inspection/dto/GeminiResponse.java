package com.tikitaka.api.batch.inspection.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class GeminiResponse {
    private List<Candidate> candidates;
    private PromptFeedback promptFeedback; // [추가] 차단 사유 수신용

    @Data
    @NoArgsConstructor
    public static class Candidate {
        private Content content;
    }
    
    @Data
    @NoArgsConstructor
    public static class PromptFeedback { // [추가]
        private String blockReason;
    }

    @Data
    @NoArgsConstructor
    public static class Content {
        private List<Part> parts;
        private String role;
    }

    @Data
    @NoArgsConstructor
    public static class Part {
        private String text;
    }
}
