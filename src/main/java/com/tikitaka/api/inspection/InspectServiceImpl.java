package com.tikitaka.api.inspection;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InspectServiceImpl implements InspectService {

    private final WebClient webClient;
    private final String geminiApiKey;
    private final String naverClientId;
    private final String naverClientSecret;

    // --- 네이버 쇼핑 API 응답 DTO ---
    @Getter @NoArgsConstructor
    private static class NaverShoppingResponse { private List<Item> items; }
    @Getter @NoArgsConstructor
    private static class Item { private String title; private String lprice; }


    public InspectServiceImpl(WebClient.Builder webClientBuilder,
                              @Value("${gemini.api.key}") String geminiApiKey,
                              @Value("${naver.api.clientId}") String naverClientId,
                              @Value("${naver.api.clientSecret}") String naverClientSecret) {
        this.webClient = webClientBuilder.build();
        this.geminiApiKey = geminiApiKey;
        this.naverClientId = naverClientId;
        this.naverClientSecret = naverClientSecret;
    }

    /**
     * [오버로딩 1] 새로 업로드된 파일(MultipartFile)을 받아 검수를 시작하는 메소드
     */
    @Override
    public InspectionResult inspectGoodsInfoWithPhotos(Goods goods, MultipartFile[] files) {
        try {
        	System.out.println("files");
        	System.out.println(files);
        	for(MultipartFile file : files) {
        		log.info(String.format("파일명 : %s, 파일원래명칭: %s, 파일사이즈: %d, 파일타입: %s", file.getName(), file.getOriginalFilename(), file.getSize(), file.getContentType()));
        	}
            // 핵심 로직은 공통 메소드에 위임하고, 파일 처리 방식만 다르게 전달합니다.
            return performInspection(goods, createPartsFromMultipartFiles(files));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * [오버로딩 2] 기존에 저장된 파일(FileContent)을 받아 검수를 시작하는 메소드
     */
    @Override
    public InspectionResult inspectGoodsInfoWithPhotos(Goods goods, List<FileContent> fileContents) {
        try {
        	for(FileContent file : fileContents) {
        		
        		log.info(String.format("파일원래명칭: %s, 파일콘텐츠: %s, 파일mime타입: %s", file.getOriginalFileName(), file.getContent(), file.getMimeType()));
        	}
            // 핵심 로직은 공통 메소드에 위임하고, 파일 처리 방식만 다르게 전달합니다.
            return performInspection(goods, createPartsFromFileContents(fileContents));
        } catch (Exception e) {
            return handleException(e);
        }
    }

    /**
     * [공통 로직] 실제 검수를 수행하는 핵심 메소드
     */
    private InspectionResult performInspection(Goods goods, List<GeminiRequest.Part> imageParts) {
        // 1. 구매가가 판매가보다 높은지 먼저 확인
        if (goods.getBuyPrice() > goods.getSalesPrice()) {
            return InspectionResult.reject("구매가가 판매가보다 높을 수 없습니다.");
        }

        // 2. 네이버 쇼핑 검색
        NaverShoppingResponse naverResponse = searchNaverShopping(goods.getGoodsName());

        // 3. 서버에서 직접 가격 적정성 검증
        validatePriceRange(goods.getSalesPrice(), naverResponse);

        // 4. Gemini API 요청 본문 생성
        GeminiRequest requestBody = createGeminiRequest(goods, imageParts, naverResponse);
        
        // 5. Gemini API 호출
        GeminiResponse geminiResponse = callGeminiApi(requestBody);
        
        // 6. 결과 파싱 및 반환
        return parseGeminiResponse(geminiResponse);
    }
    
    // --- Private Helper Methods ---

    /**
     * 네이버 쇼핑 검색 API를 호출하여 상품 정보를 가져옵니다.
     */
    private NaverShoppingResponse searchNaverShopping(String productName) {
        log.info("네이버 쇼핑 검색 API 호출: {}", productName);
        return webClient.get()
                .uri("https://openapi.naver.com/v1/search/shop.json", uriBuilder ->
                        uriBuilder.queryParam("query", productName)
                                .queryParam("display", 10)
                                .queryParam("sort", "sim")
                                .build())
                .header("X-Naver-Client-Id", naverClientId)
                .header("X-Naver-Client-Secret", naverClientSecret)
                .retrieve()
                .bodyToMono(NaverShoppingResponse.class)
                .block();
    }

    /**
     * [추가] 서버에서 직접 가격 범위를 검증하는 메소드.
     * @throws IllegalArgumentException 판매가가 허용 범위를 벗어날 경우 발생합니다.
     */
    private void validatePriceRange(long salesPrice, NaverShoppingResponse naverResponse) {
        if (naverResponse != null && naverResponse.getItems() != null && !naverResponse.getItems().isEmpty()) {
            OptionalDouble averagePriceOpt = naverResponse.getItems().stream()
                    .mapToLong(item -> Long.parseLong(item.getLprice()))
                    .average();

            if (averagePriceOpt.isPresent()) {
                double averagePrice = averagePriceOpt.getAsDouble();
                long lowerBound = (long) (averagePrice * 0.5);
                long upperBound = (long) (averagePrice * 2.0);
                
                log.info("판매가: {}, 허용 범위: {} ~ {}", salesPrice, lowerBound, upperBound);

                if (salesPrice < lowerBound || salesPrice > upperBound) {
                    String reason = String.format("등록 판매가(%,d원)가 시장 가격의 허용 범위(%,d원 ~ %,d원)를 벗어납니다.", salesPrice, lowerBound, upperBound);
                    throw new IllegalArgumentException(reason);
                }
            }
        }
    }
    
    private GeminiRequest createGeminiRequest(Goods goods, List<GeminiRequest.Part> imageParts, NaverShoppingResponse naverResponse) {
        List<GeminiRequest.Part> parts = new ArrayList<>();
        parts.add(new GeminiRequest.Part(createPrompt(goods, naverResponse)));
        parts.addAll(imageParts);
        return new GeminiRequest(List.of(new GeminiRequest.Content(parts)));
    }

    private GeminiResponse callGeminiApi(GeminiRequest requestBody) {
        String urlTemplate = "https://generativelanguage.googleapis.com/v1beta/models/{modelName}:generateContent?key={apiKey}";
        Map<String, String> uriVariables = Map.of(
            "modelName", "gemini-2.0-flash",
            "apiKey", geminiApiKey
        );
        return webClient.post()
                .uri(urlTemplate, uriVariables)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();
    }
    
    private List<GeminiRequest.Part> createPartsFromMultipartFiles(MultipartFile[] files) throws IOException {
        List<GeminiRequest.Part> imageParts = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                imageParts.add(new GeminiRequest.Part(String.format("--- 첨부 이미지 파일명: %s ---", file.getOriginalFilename())));
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
                imageParts.add(new GeminiRequest.Part(String.format("--- 첨부 이미지 파일명: %s ---", file.getOriginalFileName())));
                String base64EncodedImage = Base64.getEncoder().encodeToString(file.getContent());
                imageParts.add(new GeminiRequest.Part(new GeminiRequest.InlineData(file.getMimeType(), base64EncodedImage)));
            }
        }
        return imageParts;
    }

    /**
     * Gemini API에 전달할 프롬프트를 생성합니다.
     * AI가 단계별로 생각하고, 지정된 형식에 맞춰 답변하도록 명확한 지시사항을 포함합니다.
     *
     * @param goods           검수 대상 상품 정보
     * @param naverResponse   네이버 쇼핑 검색 API 응답 객체
     * @return Gemini API에 전달될 최종 프롬프트 문자열
     */
    private String createPrompt(Goods goods, NaverShoppingResponse naverResponse) {
        // 네이버 검색 결과에서 상품명과 가격을 함께 추출합니다.
        String marketPriceInfo = "정보 없음";
        if (naverResponse != null && naverResponse.getItems() != null && !naverResponse.getItems().isEmpty()) {
            marketPriceInfo = naverResponse.getItems().stream()
                    .map(item -> {
                        // HTML 태그를 제거하여 순수한 텍스트만 추출합니다.
                        String cleanedTitle = item.getTitle().replaceAll("<[^>]*>", "");
                        // "[상품명: ..., 최저가: ...원]" 형식으로 각 항목을 포맷합니다.
                        return String.format("[상품명: %s, 최저가: %s원]", cleanedTitle, item.getLprice());
                    })
                    .collect(Collectors.joining(", ")); // 각 항목을 쉼표로 연결합니다.
        }
        
        // AI에게 전달할 프롬프트를 생성합니다.
        String prompt = String.format(
            """
            너는 극도로 꼼꼼하고 논리적인 쇼핑몰 상품 검수 AI다. 너의 유일한 임무는 아래 '검수 절차'를 순서대로 정확히 수행하고, '출력 규칙'에 따라 최종 결론만 내리는 것이다.

            ### 검수 절차 (순서대로 진행하고, 하나라도 걸리면 즉시 반려)

            1.  **정보 교차 검증 (가장 중요):**
                - '검수 대상 정보'에 주어진 텍스트 정보(특히 '원산지')와, 첨부된 이미지 안에 보이는 모든 텍스트 정보를 비교하라.
                - 만약 두 정보가 서로 충돌한다면 (예: 원산지는 '국산'인데, 이미지에는 'Made in China'라고 적혀있다면), 즉시 '반려'하고 검수를 종료하라.

            2.  **오탈자 검수:**
                - '등록 상품명'("%s")에 명백한 오탈자가 있는가?
                - **첨부된 이미지 안의 모든 텍스트**에서 명백한 오탈자가 있는가?
                - 위 둘 중 하나라도 오탈자가 있다면 '반려'하고 검수를 종료하라.
            
            3.  **상품-이미지 일치성 검수:**
                - 판매하려는 상품과 이미지 속 핵심 물체가 일치하는가?
                - **예외 규칙:** 만약 일치하지 않더라도, 이미지 속 텍스트에 "포장재 대체"와 같은 타당한 설명이 있다면, 이 항목은 통과시켜라. 이것이 유일한 예외다.
                - 위 예외에 해당하지 않는데 불일치한다면, '반려'하고 검수를 종료하라.

            ### 검수 대상 정보
            - **등록 상품명:** %s
            - **모바일용 상품명:** %s
            - **판매가:** %,d원
            - **구매가:** %,d원
            - **원산지:** %s
            - **참고용 네이버 쇼핑 검색 결과:** [%s]

            ### 출력 규칙 (반드시 지킬 것)
            - **절대** 너의 생각이나 검수 과정을 설명하지 마라.
            - 모든 검수를 통과했을 경우, 오직 **'승인'** 이라는 한 단어만 출력하라.
            - 검수 과정에서 하나라도 문제가 발견되었다면, **'반려:'**로 시작하고 한 문장으로 명확한 이유만 출력하라. (예: 반려: 이미지 내 '특별한 할인' 문구에 오탈자가 있습니다.)

            이제 검수를 시작하고 최종 결과만 답변하라.
            """,
            goods.getGoodsName(), 
            goods.getGoodsName(), 
            goods.getMobileGoodsName(), 
            goods.getSalesPrice(), 
            goods.getBuyPrice(), 
            goods.getOrigin(), 
            marketPriceInfo
        );
        log.info(prompt);
        return prompt;
    }
    
    /**
     * Gemini API 응답을 파싱하여 검수 결과를 생성합니다.
     */
    private InspectionResult parseGeminiResponse(GeminiResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return InspectionResult.reject("AI 검수 서버로부터 유효한 응답을 받지 못했습니다.");
        }
        
        String textResponse = response.getCandidates().get(0).getContent().getParts().get(0).getText().trim();
        log.info("Gemini API 응답: {}", textResponse);

        if (textResponse.startsWith("승인")) {
            return InspectionResult.approve();
        } else if (textResponse.startsWith("반려")) {
            String reason = textResponse.length() > 3 ? textResponse.substring(3).trim() : "AI가 등록을 거부했습니다.";
            return InspectionResult.reject(reason);
        } else {
            return InspectionResult.reject("AI가 판독 불가 응답을 반환했습니다: " + textResponse);
        }
    }

    private InspectionResult handleException(Exception e) {
        if (e instanceof IllegalArgumentException) {
            log.warn("가격 검증 실패: {}", e.getMessage());
            return InspectionResult.reject(e.getMessage());
        } else if (e instanceof IOException) {
            log.error("파일 처리 중 오류 발생", e);
            return InspectionResult.reject("파일을 처리하는 중 오류가 발생했습니다.");
        } else {
            log.error("외부 API 호출 중 오류 발생", e);
            return InspectionResult.reject("AI 검수 서버 또는 외부 API 호출에 실패했습니다.");
        }
    }
}