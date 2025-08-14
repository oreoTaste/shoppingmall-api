package com.tikitaka.api.inspection;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.*;
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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Qualifier("geminiInspectService")
@Primary // 기본 구현체로 지정
public class GeminiInspectServiceImpl extends AbstractInspectService {

    private final String geminiApiKey;
    private final String geminiApiUrl;
    private final String geminiModelName;
    public GeminiInspectServiceImpl(WebClient.Builder webClientBuilder,
                                    @Value("${gemini.api.key}") String geminiApiKey,
                                    @Value("${gemini.api.url}") String geminiApiUrl,
                                    @Value("${gemini.api.model_name}") String geminiModelName,
                                    @Value("${naver.api.clientId}") String naverClientId,
                                    @Value("${naver.api.clientSecret}") String naverClientSecret) {
        // 부모 클래스에 공통 의존성 전달
        super(webClientBuilder, naverClientId, naverClientSecret);
        // 자신에게만 필요한 의존성 초기화
        this.geminiApiKey = geminiApiKey;
        this.geminiApiUrl = geminiApiUrl;
        this.geminiModelName = geminiModelName;	// gemini-2.0-flash
    }

    @Override
    protected InspectionResult performAiInspection(Goods goods, NaverShoppingResponse naverResponse, MultipartFile[] files, String forbiddenWords) throws IOException {
        // 1. Gemini 요청 형식에 맞게 파일 변환
        List<GeminiRequest.Part> imageParts = createPartsFromMultipartFiles(files);
        // 2. Gemini API 요청 본문 생성
        GeminiRequest requestBody = createGeminiRequest(goods, imageParts, naverResponse, forbiddenWords);
        // 3. Gemini API 호출
        GeminiResponse geminiResponse = callGeminiApi(requestBody);
        // 4. Gemini 응답 파싱 및 반환
        return parseGeminiResponse(geminiResponse);
    }
    
    @Override
    protected InspectionResult performAiInspection(Goods goods, NaverShoppingResponse naverResponse, List<FileContent> fileContents, String forbiddenWords) {
        // 1. Gemini 요청 형식에 맞게 파일 변환
        List<GeminiRequest.Part> imageParts = createPartsFromFileContents(fileContents);
        // 2. Gemini API 요청 본문 생성
        GeminiRequest requestBody = createGeminiRequest(goods, imageParts, naverResponse, forbiddenWords);
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

    private GeminiRequest createGeminiRequest(Goods goods, List<GeminiRequest.Part> imageParts, NaverShoppingResponse naverResponse, String forbiddenWords) {
        List<GeminiRequest.Part> parts = new ArrayList<>();
        parts.add(new GeminiRequest.Part(createPrompt(goods, naverResponse, forbiddenWords)));
        parts.addAll(imageParts);
        return new GeminiRequest(List.of(new GeminiRequest.Content(parts)));
    }

    private GeminiResponse callGeminiApi(GeminiRequest requestBody) {
        String urlTemplate = geminiApiUrl + "/v1beta/models/{modelName}:generateContent?key={apiKey}";
        Map<String, String> uriVariables = Map.of(
                "modelName", geminiModelName,
                "apiKey", geminiApiKey
        );
        return webClient.post()
                .uri(urlTemplate, uriVariables)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();
    }

    private InspectionResult parseGeminiResponse(GeminiResponse response) {
        // 1. Gemini API로부터 유효한 응답 후보가 있는지 확인
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()
                || response.getCandidates().get(0).getContent() == null
                || response.getCandidates().get(0).getContent().getParts() == null
                || response.getCandidates().get(0).getContent().getParts().isEmpty()) {
            
            return InspectionResult.reject(9, "AI 검수 서버로부터 유효한 응답을 받지 못했습니다.", geminiModelName);
        }
        
        // 2. 응답 텍스트 추출
        String textResponse = response.getCandidates().get(0).getContent().getParts().get(0).getText().trim();
        log.info("Gemini API 응답: {}", textResponse);

        // 3. 응답 케이스에 따라 분기 처리
        if (textResponse.startsWith("승인")) {
            return InspectionResult.approve(geminiModelName);

        } else if (textResponse.startsWith("반려")) {
            // "반려:[실패코드]:[사유]" 형식을 파싱
            String[] parts = textResponse.split(":", 3);

            // parts가 3개여야 정상 (e.g., ["반려", "1", "원산지 정보 불일치..."])
            if (parts.length == 3) {
                try {
                    int failureCode = Integer.parseInt(parts[1].trim());
                    String reason = parts[2].trim();
                    return InspectionResult.reject(failureCode, reason, geminiModelName);
                } catch (NumberFormatException e) {
                    // 실패코드가 숫자가 아닌 경우
                    log.error("AI 응답의 실패코드를 파싱할 수 없습니다: {}", parts[1]);
                    return InspectionResult.reject(9, "AI 응답의 실패코드 형식이 올바르지 않습니다: " + textResponse, geminiModelName);
                }
            } else {
                // "반려"로 시작하지만 형식이 맞지 않는 경우
                log.warn("AI 응답이 '반려'로 시작하지만 형식이 올바르지 않습니다: {}", textResponse);
                String reason = textResponse.length() > 3 ? textResponse.substring(3).trim() : "AI가 등록을 거부했습니다.";
                return InspectionResult.reject(9, reason, geminiModelName);
            }

        } else {
            // "승인" 또는 "반려"로 시작하지 않는 모든 그 외의 경우
            return InspectionResult.reject(9, "AI가 판독 불가 응답을 반환했습니다: " + textResponse, geminiModelName);
        }
    }

    private String createPrompt(Goods goods, NaverShoppingResponse naverResponse, String forbiddenWords) {
        String marketPriceInfo = "정보 없음";
        if (naverResponse != null && naverResponse.getItems() != null && !naverResponse.getItems().isEmpty()) {
            marketPriceInfo = naverResponse.getItems().stream()
                    .map(item -> {
                        String cleanedTitle = item.getTitle().replaceAll("<[^>]*>", "");
                        return String.format("[상품명: %s, 최저가: %s원]", cleanedTitle, item.getLprice());
                    })
                    .collect(Collectors.joining(", "));
        }
        
//        String prompt = String.format(
//                """
//		        너는 꼼꼼하고 논리적인 쇼핑몰 상품 검수 AI다. 유일한 임무는 아래 '검수 절차'를 순서대로 수행하고, '출력 규칙'에 따라 최종 결론만 내리는 것이다.
//		        # 핵심 원칙: 허위/과장 광고를 막는 것이 목표이며, 쇼핑몰의 일반적인 운영 방식(예: 포장재 대체 안내)은 제한하지 않는다.
//
//                ### 검수 절차 (순서대로 진행하고, 하나라도 위반 시 즉시 반려)
//		        1. 정보 교차 검증: '검수 대상 정보'의 텍스트(특히 원산지)와 이미지 내 텍스트가 충돌하면 즉시 반려. (예: 원산지 '국산', 이미지 'Made in China')
//		        2. 오탈자 검수: '등록 상품명'("%s")에 오탈자가 있거나, 이미지 내 텍스트에 명백한 오탈자가 있으면 반려.
//		        3.  **상품-이미지 일치성 검수:**
//		            - 등록 상품명과 이미지 속 **핵심 상품**이 일치하는가?
//		            - **매우 중요한 예외 규칙:** 판매하려는 상품(예: 사과)과 상품을 담는 **포장(박스, 봉투 등)의 디자인이 달라도 허용**한다. 이는 포장 박스 소진 시 다른 박스를 사용할 수 있음을 고객에게 안내하는 정상적인 경우다.
//		            - **[구체적인 허용 예시]** 등록 상품명이 **'청송 사과'**인데, 이미지가 **'성주 참외 박스'**라면, 이는 '사과'를 '참외 박스'에 담아 보낼 수 있다는 의미이므로 **'승인'** 대상이다. 상품명과 박스의 과일 이름이 다르더라도 통과시켜라.
//		            - **[반려 예시]** 등록 상품명이 **'사과'**인데, 이미지에 **'자동차'**나 **'컴퓨터'**처럼 상품과 전혀 관련 없는 물체가 있다면 이는 명백한 불일치이므로 '반려'하라.
//
//                ### 검수 대상 정보
//                - **등록 상품명:** %s
//                - **모바일용 상품명:** %s
//                - **판매가:** %,d원
//                - **구매가:** %,d원
//                - **원산지:** %s
//                - **참고용 네이버 쇼핑 검색 결과:** [%s]
//
//		        ### 출력 규칙
//		        - 절대 검수 과정이나 생각을 설명하지 말 것.
//		        - 모든 검수 통과 시: 오직 '승인' 한 단어만 출력.
//		        - 문제 발견 시: '반려:'로 시작하고 한 문장으로 명확한 이유만 출력.
//
//        		검수를 시작하고 최종 결과만 답변하라.
//                """,
//                goods.getGoodsName(), 
//                goods.getGoodsName(), 
//                goods.getMobileGoodsName(), 
//                goods.getSalesPrice(), 
//                goods.getBuyPrice(), 
//                goods.getOrigin(), 
//                marketPriceInfo
//        );
        String prompt = String.format(
                """
                너는 정해진 규칙을 철저히 따르는 쇼핑몰 상품 검수 AI다. 너의 유일한 임무는 아래 '검수 절차'를 1번부터 순서대로 수행하고, 첫 번째 위반 항목이 발견되는 즉시 '출력 규칙'에 따라 최종 결론만 내리는 것이다.

                ### 검수 절차 (순서대로 진행하고, 하나라도 위반 시 즉시 중단 및 해당 코드로 반려)
                1.  **정보 교차 검증 (실패코드: 1):** '검수 대상 정보'의 텍스트(특히 원산지)와 이미지 내 텍스트 정보가 충돌하는가? (예: 원산지 '국산', 이미지 'Made in China')
                2.  **오탈자 검수 (실패코드: 2):** '등록 상품명'("%s") 또는 이미지 내 텍스트에 명백한 오탈자가 있는가?
                3.  **상품-이미지 불일치 검수 (실패코드: 3):** 등록 상품명과 이미지 속 핵심 상품이 명백히 다른가?
                    - **중요 예외:** 포장 박스 디자인이 다른 것은 허용한다. (예: '사과'를 '참외 박스'에 담아 파는 것은 허용)
                    - **반려 예시:** '사과'를 파는데 이미지에 '자동차'가 있는 경우.
                4.  **금칙어 포함 여부 검수 (실패코드: 4):** '등록 상품명' 또는 '모바일용 상품명' 또는 이미지 내 텍스트 정보 내에 아래 '금칙어 목록'에 있는 단어가 포함되어 있는가?

                ### 검수 대상 정보
                - **등록 상품명:** %s
                - **모바일용 상품명:** %s
                - **판매가:** %,d원
                - **구매가:** %,d원
                - **원산지:** %s
                - **참고용 네이버 쇼핑 검색 결과:** [%s]
                - **금칙어 목록:** [%s]

                ### 출력 규칙
                - 절대 검수 과정이나 생각을 설명하지 말 것.
                - **성공 시:** 모든 검수 절차를 통과했을 경우, 오직 '승인' 한 단어만 출력한다.
                - **실패 시:** 검수 절차 중 하나라도 위반하는 경우, 즉시 검수를 중단하고 `반려:[실패코드]:[한 문장으로 된 명확한 반려 사유]` 형식으로만 출력한다.
                - **[실패 출력 예시]**
                - 반려:1:원산지는 국산으로 표기되었으나 이미지에서 Made in China 문구가 확인됩니다.
                - 반려:2:등록 상품명에 '달콤한'이 '닳콤한'으로 오타 표기되었습니다.
                - 반려:4:상품명에 금칙어인 '예시금칙어1'이 포함되어 있습니다.

                검수를 시작하고 최종 결과만 답변하라.
                """,
                // 검수 절차 2번
                goods.getGoodsName(),
                // 검수 대상 정보
                goods.getGoodsName(),
                goods.getMobileGoodsName(),
                goods.getSalesPrice(),
                goods.getBuyPrice(),
                goods.getOrigin(),
                marketPriceInfo,
                forbiddenWords
        );
        log.info("생성된 프롬프트:\n{}", prompt);
        return prompt;
    }

}