package com.tikitaka.api.batch.inspection;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.api.batch.goods.entity.Goods;
import com.tikitaka.api.batch.inspection.dto.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@Qualifier("chatGPTInspectService")
//@Primary // 기본 구현체로 지정
public class ChatGPTInspectBatchServiceImpl extends AbstractInspectBatchService {

    private final String openaiApiKey;
	private final String openaiApiUrl;
    private final String openaiApiModelName;

    private final ObjectMapper objectMapper; // ObjectMapper 필드 추가
    
    // --- ChatGPT용 요청/응답 DTO ---
    @Getter
    private static class ChatGPTRequest {
        private final String model;
        private final List<Message> messages;
        private final int max_tokens = 1024; // 응답 최대 길이 설정

        public ChatGPTRequest(String model, List<Message> messages) {
            this.model = model;
            this.messages = messages;
        }
    }

    @Getter @AllArgsConstructor
    @JsonPropertyOrder({ "role", "content" })
    private static class Message {
        private final String role = "user";
        private final List<Content> content;
    }

    @Getter
    private static abstract class Content {
        private final String type;
        protected Content(String type) { this.type = type; }
    }

    @Getter
    @JsonPropertyOrder({ "type", "text" })
    private static class TextContent extends Content {
        private final String text;
        public TextContent(String text) { super("text"); this.text = text; }
    }

    @Getter
    private static class ImageUrlContent extends Content {
        private final ImageUrl image_url;
        public ImageUrlContent(String url) { super("image_url"); this.image_url = new ImageUrl(url); }
    }
    
    @Getter @AllArgsConstructor
    private static class ImageUrl { private final String url; }

    @Getter @NoArgsConstructor
    private static class ChatGPTResponse {
        private List<Choice> choices;
    }

    @Getter @NoArgsConstructor
    private static class Choice {
        private ResponseMessage message;
    }

    @Getter @NoArgsConstructor
    private static class ResponseMessage {
        private String content;
    }
    
    /**
     * 생성자: 공통 의존성은 부모에게, 전용 의존성(API Key)은 여기서 초기화합니다.
     */
    public ChatGPTInspectBatchServiceImpl(WebClient.Builder webClientBuilder,
                                     @Value("${openai.api.key}") String openaiApiKey,
                                     @Value("${openai.api.url}") String openaiApiUrl,
                                     @Value("${openai.api.model_name}") String openaiApiModelName,
                                     ObjectMapper objectMapper) {
        super(webClientBuilder);
        this.openaiApiKey = openaiApiKey;
        this.openaiApiUrl = openaiApiUrl;
        this.objectMapper = objectMapper;
        this.openaiApiModelName = openaiApiModelName;
    }

	@Override
	protected String getInspectorId() {
		return openaiApiModelName;
	}
	
    @Override
    public InspectionResult performAiInspection(Goods goods, MultipartFile[] files, String forbiddenWords) throws IOException {
        List<Content> contents = new ArrayList<>();
        // 1. 프롬프트(텍스트)를 Content 리스트에 추가
        contents.add(new TextContent(createPromptForCheckForbiddenWords(goods, forbiddenWords)));
        // 2. 이미지들을 Base64로 인코딩하여 Content 리스트에 추가
        contents.addAll(createImageContentsFromMultipartFiles(files));

        // 3. ChatGPT API 요청 생성 및 호출
        ChatGPTRequest request = new ChatGPTRequest(openaiApiModelName, List.of(new Message(contents)));
        ChatGPTResponse response = callChatGptApi(request);

        // 4. 응답 파싱 및 반환
        return parseChatGPTResponse(response);
    }
    
    @Override
    public InspectionResult performAiInspection(Goods goods, List<FileContent> fileContents, String forbiddenWords) {
        List<Content> contents = new ArrayList<>();
        // 1. 프롬프트(텍스트)를 Content 리스트에 추가
        contents.add(new TextContent(createPromptForCheckForbiddenWords(goods, forbiddenWords)));
        // 2. 이미지들을 Base64로 인코딩하여 Content 리스트에 추가
        contents.addAll(createImageContentsFromFileContents(fileContents));
        
        // 3. ChatGPT API 요청 생성 및 호출
        ChatGPTRequest request = new ChatGPTRequest(openaiApiModelName, List.of(new Message(contents)));
        ChatGPTResponse response = callChatGptApi(request);

        // 4. 응답 파싱 및 반환
        return parseChatGPTResponse(response);
    }

    // --- Private Helper Methods ---
    
    private List<ImageUrlContent> createImageContentsFromMultipartFiles(MultipartFile[] files) throws IOException {
        List<ImageUrlContent> imageContents = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                byte[] fileBytes = file.getBytes();
                String base64Image = Base64.getEncoder().encodeToString(fileBytes);
                String dataUrl = "data:" + file.getContentType() + ";base64," + base64Image;
                imageContents.add(new ImageUrlContent(dataUrl));
            }
        }
        return imageContents;
    }
    
    private List<ImageUrlContent> createImageContentsFromFileContents(List<FileContent> fileContents) {
        List<ImageUrlContent> imageContents = new ArrayList<>();
        if (fileContents != null) {
            for (FileContent file : fileContents) {
                String base64Image = Base64.getEncoder().encodeToString(file.getContent());
                String dataUrl = "data:" + file.getMimeType() + ";base64," + base64Image;
                imageContents.add(new ImageUrlContent(dataUrl));
            }
        }
        return imageContents;
    }

    private ChatGPTResponse callChatGptApi(ChatGPTRequest requestBody) {
        try {
            // 이 부분이 핵심! 요청 객체를 JSON 문자열로 변환하여 로그로 남긴다.
            String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
            log.info("OpenAI API Request Body:\n{}", jsonRequest);
        } catch (Exception e) {
            log.error("Failed to serialize request body to JSON", e);
        }

        return webClient.post()
                .uri(this.openaiApiUrl + "/v1/chat/completions") // 주소는 이게 맞습니다.
                .header("Authorization", "Bearer " + openaiApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(ChatGPTResponse.class)
                .block();
    }

    private InspectionResult parseChatGPTResponse(ChatGPTResponse response) {
        // 1. ChatGPT API로부터 유효한 응답 후보가 있는지 확인 (Null-safety 강화)
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || response.getChoices().get(0).getMessage().getContent() == null) {
            
            return InspectionResult.reject(9, "AI 검수 서버로부터 유효한 응답을 받지 못했습니다.", openaiApiModelName);
        }
        
        // 2. 응답 텍스트 추출
        String textResponse = response.getChoices().get(0).getMessage().getContent().trim();
        log.info("ChatGPT API 응답: {}", textResponse);

        // 3. 응답 케이스에 따라 분기 처리
        if (textResponse.startsWith("승인")) {
            return InspectionResult.approve(openaiApiModelName);

        } else if (textResponse.startsWith("반려")) {
            // "반려:[실패코드]:[사유]" 형식을 파싱
            String[] parts = textResponse.split(":", 3);
            
            // parts가 3개여야 정상 (e.g., ["반려", "1", "원산지 정보 불일치..."])
            if (parts.length == 3) {
                try {
                    int failureCode = Integer.parseInt(parts[1].trim());
                    String reason = parts[2].trim();
                    return InspectionResult.reject(failureCode, reason, openaiApiModelName);
                } catch (NumberFormatException e) {
                    // 실패코드가 숫자가 아닌 경우
                    log.error("ChatGPT 응답의 실패코드를 파싱할 수 없습니다: {}", parts[1]);
                    return InspectionResult.reject(9, "AI 응답의 실패코드 형식이 올바르지 않습니다: " + textResponse, openaiApiModelName);
                }
            } else {
                // "반려"로 시작하지만 형식이 맞지 않는 경우
                log.warn("ChatGPT 응답이 '반려'로 시작하지만 형식이 올바르지 않습니다: {}", textResponse);
                String reason = textResponse.length() > 3 ? textResponse.substring(3).trim() : "AI가 등록을 거부했습니다.";
                return InspectionResult.reject(9, reason, openaiApiModelName);
            }

        } else {
            // "승인" 또는 "반려"로 시작하지 않는 모든 그 외의 경우 (문자열 따옴표 오류 수정)
            return InspectionResult.reject(9, "AI가 판독 불가 응답을 반환했습니다: " + textResponse, openaiApiModelName);
        }
    }

    private String createPromptForCheckForbiddenWords(Goods goods, String forbiddenWords) {
//        String prompt = String.format(
//                """
//                너는 정해진 규칙을 철저히 따르는 쇼핑몰 상품 검수 AI다. 너의 유일한 임무는 아래 '검수 절차'를 1번부터 순서대로 수행하고, 첫 번째 위반 항목이 발견되는 즉시 '출력 규칙'에 따라 최종 결론만 내리는 것이다.
//
//                ### 검수 절차 (순서대로 진행하고, 하나라도 위반 시 즉시 중단 및 해당 코드로 반려)
//                1.  **정보 교차 검증 (실패코드: 1):** '검수 대상 정보'의 텍스트(특히 원산지)와 이미지 내 텍스트 정보가 충돌하는가? (예: 원산지 '국산', 이미지 'Made in China')
//                2.  **오탈자 검수 (실패코드: 2):** '등록 상품명'("%s") 또는 이미지 내 텍스트에 명백한 오탈자가 있는가?
//                3.  **상품-이미지 불일치 검수 (실패코드: 3):** 등록 상품명과 이미지 속 핵심 상품이 명백히 다른가?
//                    - **중요 예외:** 포장 박스 디자인이 다른 것은 허용한다. (예: '사과'를 '참외 박스'에 담아 파는 것은 허용)
//                    - **반려 예시:** '사과'를 파는데 이미지에 '자동차'가 있는 경우.
//                4.  **금칙어 포함 여부 검수 (실패코드: 4):** '등록 상품명' 또는 '모바일용 상품명' 또는 이미지 내 텍스트 정보 내에 아래 '금칙어 목록'에 있는 단어가 포함되어 있는가?
//
//                ### 검수 대상 정보
//                - **등록 상품명:** %s
//                - **모바일용 상품명:** %s
//                - **판매가:** %,d원
//                - **구매가:** %,d원
//                - **금칙어 목록:** [%s]
//                - **기타 공시사항:** %s
//
//                ### 출력 규칙
//                - 절대 검수 과정이나 생각을 설명하지 말 것.
//                - **성공 시:** 모든 검수 절차를 통과했을 경우, 오직 '승인' 한 단어만 출력한다.
//                - **실패 시:** 검수 절차 중 하나라도 위반하는 경우, 즉시 검수를 중단하고 `반려:[실패코드]:[한 문장으로 된 명확한 반려 사유]` 형식으로만 출력한다.
//                - **[실패 출력 예시]**
//                - 반려:1:원산지는 국산으로 표기되었으나 이미지에서 Made in China 문구가 확인됩니다.
//                - 반려:2:등록 상품명에 '달콤한'이 '닳콤한'으로 오타 표기되었습니다.
//                - 반려:4:상품명에 금칙어인 '예시금칙어1'이 포함되어 있습니다.
//
//                검수를 시작하고 최종 결과만 답변하라.
//                """,
//                // 검수 절차 2번
//                goods.getGoodsName(),
//                // 검수 대상 정보
//                goods.getGoodsName(),
//                goods.getMobileGoodsName(),
//                goods.getSalePrice(),
//                goods.getBuyPrice(),
//                forbiddenWords,
//                goods.getGoodsInfo()
//        );
    	
    	String goodsInfoLine = goods.getGoodsInfo().trim().length() <= 0 ? "" : String.format("\n- **기타 공시사항:** %s", goods.getGoodsInfo().trim());
    	String prompt = String.format(
    	        """
    	        너는 쇼핑몰 상품 정보에서 금칙어를 탐지하는 AI다. 너의 유일한 임무는 '검수 대상 텍스트'에 '금칙어 목록'의 단어가 포함되어 있는지 확인하고, '출력 규칙'에 따라 최종 결론만 내리는 것이다.

    	        ### 검수 규칙
    	        - **금칙어 포함 여부 검수 (실패코드: 4):** '검수 대상 텍스트' 또는 첨부된 이미지 속 텍스트에 '금칙어 목록'에 있는 단어가 하나라도 포함되어 있는가?

    	        ### 검수 대상 텍스트
    	        - **등록 상품명:** %s
    	        - **모바일용 상품명:** %s%s
    	        
    	        ### 금칙어 목록
    	        - [%s]

    	        ### 출력 규칙
    	        - 절대 검수 과정이나 부가적인 설명을 하지 말 것.
    	        - **금칙어 미포함 시:** 오직 '승인' 한 단어만 출력한다.
    	        - **금칙어 포함 시:** `반려:4:[금칙어가 포함된 문구]에서 금칙어 '[발견된 금칙어]'가 발견되었습니다.` 형식으로만 출력한다.
    	        - **[실패 출력 예시]**
    	        - 반려:4:등록 상품명 '최고급 명품 담배'에서 금칙어 '담배'가 발견되었습니다.

    	        검수를 시작하고 최종 결과만 답변하라.
    	        """,
    	        // 검수 대상 정보
    	        goods.getGoodsName(),
    	        goods.getMobileGoodsName(),
    	        goodsInfoLine,
    	        forbiddenWords
    	);
        log.info("생성된 프롬프트:\n{}", prompt);
        return prompt;
    }

}