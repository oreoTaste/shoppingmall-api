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
            
            return InspectionResult.reject(100, null, "AI 검수 서버로부터 유효한 응답을 받지 못했습니다.", openaiApiModelName);
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
                	String forbiddenWord = parts[1].trim();
                    String reason = parts[2].trim();
                    return InspectionResult.reject(200, forbiddenWord, reason, openaiApiModelName);
                } catch (NumberFormatException e) {
                    // 실패코드가 숫자가 아닌 경우
                    log.error("ChatGPT 응답의 실패코드를 파싱할 수 없습니다: {}", parts[1]);
                    return InspectionResult.reject(300, null, "AI 응답의 실패코드 형식이 올바르지 않습니다: " + textResponse, openaiApiModelName);
                }
            } else {
                // "반려"로 시작하지만 형식이 맞지 않는 경우
                log.warn("ChatGPT 응답이 '반려'로 시작하지만 형식이 올바르지 않습니다: {}", textResponse);
                String reason = textResponse.length() > 3 ? textResponse.substring(3).trim() : "AI가 등록을 거부했습니다.";
                return InspectionResult.reject(400, null, reason, openaiApiModelName);
            }

        } else {
            // "승인" 또는 "반려"로 시작하지 않는 모든 그 외의 경우 (문자열 따옴표 오류 수정)
            return InspectionResult.reject(500, null, "AI가 판독 불가 응답을 반환했습니다: " + textResponse, openaiApiModelName);
        }
    }

    private String createPromptForCheckForbiddenWords(Goods goods, String forbiddenWords) {
    	String goodsInfo = goods.getGoodsInfo();
    	String cleanedGoodsInfo = "";

    	if (goodsInfo != null && !goodsInfo.trim().isEmpty()) {
    	    log.info("Original goodsInfo before cleaning: [{}]", goodsInfo);
    	    cleanedGoodsInfo = goodsInfo.replaceAll("(?s)<[^>]*>", "").trim();
    	    log.info("Cleaned goodsInfo after regex: [{}]", cleanedGoodsInfo);
    	}

    	String goodsInfoLine = cleanedGoodsInfo.isEmpty() ? "" : String.format("\n- **기타 공시사항:** %s", cleanedGoodsInfo);

    	String prompt = String.format(
    	        """
    	        너는 쇼핑몰 상품의 텍스트와 이미지에서 금칙어와 그 변형을 탐지하는 AI 검수 시스템이다.
    	        주어진 입력 정보를 바탕으로 아래 과업과 출력 규칙에 따라 최종 결과만 반환하라.

    	        ### 입력 정보
    	        [텍스트 정보]
    	        - **등록 상품명:** %s
    	        - **모바일용 상품명:** %s%s

    	        [이미지 정보]
    	        - 함께 제공된 이미지에서 모든 텍스트를 인식하라.

    	        ### 금칙어 목록
    	        - %s

    	        ### 수행 과업
    	        1.  `[텍스트 정보]`와 `[이미지 정보]` 전체에서 `[금칙어 목록]`에 포함된 단어 또는 **이를 의도적으로 변형/우회한 표현**이 있는지 검사하라.
    	        2.  **[우회 패턴]** 은 아래와 같은 경우를 포함한다.
    	            - **유사 발음:** '병신' -> '병쉰', '븅신'
    	            - **특수문자 삽입/대체:** '병신' -> '병!신', '병@신'
    	            - **자음/모음 분리:** '병신' -> 'ㅂㅅ', 'ㅂㅕㅇㅅㅣㄴ'
    	            - **기타 오타 및 회피 시도**
    	        3.  검사 순서는 **등록 상품명 -> 모바일용 상품명 -> 기타 공시사항 -> 이미지 내 텍스트** 순으로 진행한다.
    	        4.  금칙어 또는 그 변형이 발견되면 즉시 검사를 중단하고 `[출력 규칙]`에 따라 결과를 반환한다.

    	        ### 출력 규칙
    	        - **규칙 1 (미포함):** 검사 후 금칙어 및 변형이 발견되지 않으면, 오직 **'승인'** 이라고만 응답한다.
    	        - **규칙 2 (포함):** 금칙어 또는 그 변형이 발견되면, **가장 먼저 발견된 하나**에 대해서만 `반려:[원본 금칙어]:[사유]` 형식으로 응답한다.
    	            - **[사유] 작성법:**
    	                - **텍스트에서 발견 시:** `[검수 항목]에서 금칙어 '[원본 금칙어]'의 변형 표현('[발견된 표현]') 발견`
    	                - **이미지에서 발견 시:** `이미지에서 금칙어 '[원본 금칙어]'의 변형 표현('[발견된 표현]') 발견`
    	        - **규칙 3 (예외):** 절대 설명, 인사, 사과 등 다른 말을 덧붙이지 않는다.

    	        ### 출력 예시
    	        - **금칙어가 없는 경우:**
    	        승인
    	        - **'등록 상품명'에서 금칙어 '최고'가 발견된 경우:**
    	        반려:최고:등록 상품명에서 금칙어 '최고'의 변형 표현('최고') 발견
    	        - **이미지에서 금칙어 '병신'의 변형인 '병!신'이 발견된 경우:**
    	        반려:병신:이미지에서 금칙어 '병신'의 변형 표현('병!신') 발견

    	        이제 지시사항에 따라 검수를 시작하고 최종 결과만 출력하라.
    	        """,
    	        // 검수 대상 정보
    	        goods.getGoodsName(),
    	        goods.getMobileGoodsName(),
    	        goodsInfoLine,
    	        forbiddenWords
    	);
    	log.debug("생성된 프롬프트:\n{}", prompt);
    	return prompt;
    }

}