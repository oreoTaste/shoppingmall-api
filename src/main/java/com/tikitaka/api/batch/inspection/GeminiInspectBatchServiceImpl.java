package com.tikitaka.api.batch.inspection;

import com.tikitaka.api.batch.goods.entity.Goods;
import com.tikitaka.api.batch.inspection.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Qualifier("geminiInspectService")
@Primary // 기본 구현체로 지정
public class GeminiInspectBatchServiceImpl extends AbstractInspectBatchService {

    private final String geminiApiKey;
    private final String geminiApiUrl;
    private final String geminiModelName;
    public GeminiInspectBatchServiceImpl(WebClient.Builder webClientBuilder,
                                    @Value("${gemini.api.key}") String geminiApiKey,
                                    @Value("${gemini.api.url}") String geminiApiUrl,
                                    @Value("${gemini.api.model_name}") String geminiModelName) {
        // 부모 클래스에 공통 의존성 전달
        super(webClientBuilder);
        // 자신에게만 필요한 의존성 초기화
        this.geminiApiKey = geminiApiKey;
        this.geminiApiUrl = geminiApiUrl;
        this.geminiModelName = geminiModelName;	// gemini-2.0-flash
    }

    @Override
    public InspectionResult performAiInspection(Goods goods, MultipartFile[] files, String forbiddenWords) throws IOException {
        // 1. Gemini 요청 형식에 맞게 파일 변환
        List<GeminiRequest.Part> imageParts = createPartsFromMultipartFiles(files);
        // 2. Gemini API 요청 본문 생성
        GeminiRequest requestBody = createGeminiRequest(goods, imageParts, forbiddenWords);
        // 3. Gemini API 호출
        GeminiResponse geminiResponse = callGeminiApi(requestBody);
        // 4. Gemini 응답 파싱 및 반환
        return parseGeminiResponse(geminiResponse);
    }
    
    @Override
    public InspectionResult performAiInspection(Goods goods, List<FileContent> fileContents, String forbiddenWords) {
        // 1. Gemini 요청 형식에 맞게 파일 변환
        List<GeminiRequest.Part> imageParts = createPartsFromFileContents(fileContents);
        // 2. Gemini API 요청 본문 생성
        GeminiRequest requestBody = createGeminiRequest(goods, imageParts, forbiddenWords);
        // 3. Gemini API 호출
        GeminiResponse geminiResponse = callGeminiApi(requestBody);
        // 4. Gemini 응답 파싱 및 반환
        return parseGeminiResponse(geminiResponse);
    }
    
    // --- 아래부터는 모두 GeminiInspectService에만 종속적인 Private Helper Methods ---
	@Override
	protected String getInspectorId() {
		return geminiModelName;
	}

    private List<GeminiRequest.Part> createPartsFromMultipartFiles(MultipartFile[] files) throws IOException {
        List<GeminiRequest.Part> imageParts = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
            	String fileName = String.format("--- 첨부 이미지 파일명: %s ---", file.getOriginalFilename());
                log.info("파일명 : {}", fileName);
                imageParts.add(new GeminiRequest.Part(fileName));
                byte[] fileBytes = file.getBytes();
                String base64EncodedImage = Base64.getEncoder().encodeToString(fileBytes);
                imageParts.add(new GeminiRequest.Part(new GeminiRequest.InlineData(file.getContentType(), base64EncodedImage)));
            }
        }
        return imageParts;
    }

    private List<GeminiRequest.Part> createPartsFromFileContents(List<FileContent> fileContents) {
        List<GeminiRequest.Part> imageParts = new ArrayList<>();
        if (fileContents != null) {
            for (FileContent file : fileContents) {
            	String fileName = String.format("--- 첨부 이미지 파일명: %s ---", file.getOriginalFileName());
                log.info("파일명 : {}", fileName);
                imageParts.add(new GeminiRequest.Part(fileName));
                String base64EncodedImage = Base64.getEncoder().encodeToString(file.getContent());
                imageParts.add(new GeminiRequest.Part(new GeminiRequest.InlineData(file.getMimeType(), base64EncodedImage)));
            }
        }
        return imageParts;
    }

    private GeminiRequest createGeminiRequest(Goods goods, List<GeminiRequest.Part> imageParts, String forbiddenWords) {
        List<GeminiRequest.Part> parts = new ArrayList<>();
        parts.add(new GeminiRequest.Part(createPromptForCheckForbiddenWords(goods, forbiddenWords)));
        parts.addAll(imageParts);
        return new GeminiRequest(List.of(new GeminiRequest.Content(parts)));
    }
    
    private GeminiResponse callGeminiApi(GeminiRequest requestBody) {
        String urlTemplate = geminiApiUrl + "/v1beta/models/{modelName}:generateContent?key={apiKey}";
        Map<String, String> uriVariables = Map.of(
                "modelName", geminiModelName,
                "apiKey", geminiApiKey
        );
        
        try { // <-- [추가] try-catch 블록 시작
            return webClient.post()
                    .uri(urlTemplate, uriVariables)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();
        } catch (WebClientResponseException e) { // <-- [추가] WebClientResponseException 처리
            log.error("Gemini API 호출 중 오류 발생 - Status: {}, Response Body: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(StandardCharsets.UTF_8)); // 응답 본문을 UTF-8로 디코딩하여 로그에 기록
            throw e; // 예외를 다시 던져서 상위 서비스에서 처리하도록 함
        }
    }


    private InspectionResult parseGeminiResponse(GeminiResponse response) {
        // 1. Gemini API로부터 유효한 응답 후보가 있는지 확인
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()
                || response.getCandidates().get(0).getContent() == null
                || response.getCandidates().get(0).getContent().getParts() == null
                || response.getCandidates().get(0).getContent().getParts().isEmpty()) {
            
            return InspectionResult.reject(null, "AI 검수 서버로부터 유효한 응답을 받지 못했습니다.", geminiModelName);
        }
        
        // 2. 응답 텍스트 추출
        String textResponse = response.getCandidates().get(0).getContent().getParts().get(0).getText().trim();
        log.info("Gemini API 응답: {}", textResponse);

        // 3. 응답 케이스에 따라 분기 처리
        if (textResponse.startsWith("승인")) {
            return InspectionResult.approve(geminiModelName);

        } else if (textResponse.startsWith("반려")) {
            // "반려:금칙어:[사유]" 형식을 파싱
            String[] parts = textResponse.split(":", 3);

            // parts가 3개여야 정상 (e.g., ["반려", "금칙어", "원산지 정보 불일치..."])
            if (parts.length == 3) {
                try {
                    String forbiddenWord = parts[1].trim();
                    String reason = parts[2].trim();
                    return InspectionResult.reject(forbiddenWord, reason, geminiModelName);
                } catch (NumberFormatException e) {
                    // 실패코드가 숫자가 아닌 경우
                    log.error("AI 응답의 실패코드를 파싱할 수 없습니다: {}", parts[1]);
                    return InspectionResult.reject(null, "AI 응답의 실패코드 형식이 올바르지 않습니다: " + textResponse, geminiModelName);
                }
            } else {
                // "반려"로 시작하지만 형식이 맞지 않는 경우
                log.warn("AI 응답이 '반려'로 시작하지만 형식이 올바르지 않습니다: {}", textResponse);
                String reason = textResponse.length() > 3 ? textResponse.substring(3).trim() : "AI가 등록을 거부했습니다.";
                return InspectionResult.reject(null, reason, geminiModelName);
            }

        } else {
            // "승인" 또는 "반려"로 시작하지 않는 모든 그 외의 경우
            return InspectionResult.reject(null, "AI가 판독 불가 응답을 반환했습니다: " + textResponse, geminiModelName);
        }
    }

    private String createPromptForCheckForbiddenWords(Goods goods, String forbiddenWords) {
    	String goodsInfo = goods.getGoodsInfo();
    	String cleanedGoodsInfo = "";

    	if (goodsInfo != null && !goodsInfo.trim().isEmpty()) {
    	    cleanedGoodsInfo = goodsInfo.replaceAll("(?s)<[^>]*>", "").trim();
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
		            - **[사유] 작성법:** `[발견 위치]에서 금칙어 '[원본 금칙어]'의 변형 표현('[발견된 표현]') 발견`
		            - **[발견 위치] 표기 표준:** 발견된 곳에 따라 아래 명칭 중 하나를 반드시 사용한다.
		                - '등록 상품명'
		                - '모바일용 상품명'
		                - '기타 공시사항'
		                - '이미지'
		        - **규칙 3 (예외):** 절대 설명, 인사, 사과 등 다른 말을 덧붙이지 않는다.
		
		        ### 출력 예시
		        - **금칙어가 없는 경우:**
		        승인
		        - **'등록 상품명'에서 금칙어 '최고'가 발견된 경우:**
		        반려:최고:등록 상품명에서 금칙어 '최고'의 변형 표현('최고') 발견
		        - **'기타 공시사항'에서 금칙어 '카톡'의 변형이 발견된 경우:**
		        반려:카톡:기타 공시사항에서 금칙어 '카톡'의 변형 표현('ㅋㅏ톡') 발견
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
    	log.info("생성된 프롬프트:\n{}", prompt);
    	return prompt;
    }



}