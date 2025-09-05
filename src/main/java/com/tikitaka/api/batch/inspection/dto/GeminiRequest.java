package com.tikitaka.api.batch.inspection.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

// --- Gemini API 요청 DTO ---
@Data
@AllArgsConstructor
public class GeminiRequest {
    private List<Content> contents;

    @Data
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL) // null이 아닌 필드만 JSON에 포함
    public static class Part {
        private String text;
        private InlineData inlineData;

        public Part(String text) {
            this.text = text;
        }

        public Part(InlineData inlineData) {
            this.inlineData = inlineData;
        }
    }

    @Data
    @AllArgsConstructor
    public static class InlineData {
        private String mimeType;
        private String data; // Base64 encoded string
    }
}