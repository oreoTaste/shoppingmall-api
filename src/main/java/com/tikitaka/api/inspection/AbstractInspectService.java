package com.tikitaka.api.inspection;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
public abstract class AbstractInspectService implements InspectService {

    protected final WebClient webClient;
    private final String naverClientId;
    private final String naverClientSecret;

    // --- 공통 DTO ---
    @Getter
    @NoArgsConstructor
    protected static class NaverShoppingResponse {
        private List<Item> items;
    }

    @Getter
    @NoArgsConstructor
    protected static class Item {
        private String title;
        private String lprice;
    }
    
    /**
     * 공통으로 필요한 의존성을 주입받는 생성자
     */
    public AbstractInspectService(WebClient.Builder webClientBuilder,
                                  String naverClientId,
                                  String naverClientSecret) {
        this.webClient = webClientBuilder.build();
        this.naverClientId = naverClientId;
        this.naverClientSecret = naverClientSecret;
    }

    // --- 자식 클래스가 반드시 구현해야 할 핵심 메소드 ---
    
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다.
     * @param goods 검수 대상 상품
     * @param naverResponse 네이버 쇼핑 검색 결과
     * @param files 검수용 이미지 파일
     * @return 검수 결과
     * @throws IOException 파일 처리 오류
     */
    protected abstract InspectionResult performAiInspectionWithPriceInfo(Goods goods, NaverShoppingResponse naverResponse, MultipartFile[] files, String forbiddenWords) throws IOException;
    
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다. (오버로딩)
     * @param goods 검수 대상 상품
     * @param naverResponse 네이버 쇼핑 검색 결과
     * @param fileContents 검수용 기존 이미지 파일 정보
     * @return 검수 결과
     */
    protected abstract InspectionResult performAiInspectionWithPriceInfo(Goods goods, NaverShoppingResponse naverResponse, List<FileContent> fileContents, String forbiddenWords);
    
    
    // --- 인터페이스를 구현하는 공통 메소드 ---

    @Override
    public final InspectionResult inspectGoodsInfoWithPhotos(Goods goods, MultipartFile[] files, String forbiddenWords) {
        try {
        	
            // 1. 공통 전처리: 구매가-판매가 확인
            if (goods.getBuyPrice() > goods.getSalesPrice()) {
                return InspectionResult.reject(9, "구매가가 판매가보다 높을 수 없습니다.", getInspectorId());
            }
            // 2. 공통 전처리: 네이버 쇼핑 검색 및 가격 검증
            NaverShoppingResponse naverResponse = searchNaverShopping(goods.getGoodsName());
            validatePriceRange(goods.getSalesPrice(), naverResponse);
            
            // 3. AI별 실제 검수는 자식 클래스에 위임
            return performAiInspectionWithPriceInfo(goods, naverResponse, files, forbiddenWords);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    @Override
    public final InspectionResult inspectGoodsInfoWithPhotos(Goods goods, List<FileContent> fileContents, String forbiddenWords) {
        try {
            // 1. 공통 전처리: 구매가-판매가 확인
            if (goods.getBuyPrice() > goods.getSalesPrice()) {
                return InspectionResult.reject(9, "구매가가 판매가보다 높을 수 없습니다.", getInspectorId());
            }
            // 2. 공통 전처리: 네이버 쇼핑 검색 및 가격 검증
            NaverShoppingResponse naverResponse = searchNaverShopping(goods.getGoodsName());
            validatePriceRange(goods.getSalesPrice(), naverResponse);

            // 3. AI별 실제 검수는 자식 클래스에 위임
            return performAiInspectionWithPriceInfo(goods, naverResponse, fileContents, forbiddenWords);

        } catch (Exception e) {
            return handleException(e);
        }
    }
    
    // --- 공통 Private Helper Methods ---
    protected abstract String getInspectorId();
    
    private NaverShoppingResponse searchNaverShopping(String productName) {
        log.info("네이버 쇼핑 검색 API 호출: {}", productName);
        // 1. URI 객체를 미리 생성합니다.
        URI uri = UriComponentsBuilder
                .fromUriString("https://openapi.naver.com/v1/search/shop.json")
                .queryParam("query", productName)
                .queryParam("display", 10)
                .queryParam("sort", "sim")
                .encode() // URL 인코딩(예: 공백 -> %20)을 보장합니다.
                .build()
                .toUri();

        // 2. 생성된 URI를 로그로 출력합니다.
        log.info("네이버 요청 URL: {}", uri.toString());
        
        // 3. WebClient에는 완성된 URI 객체를 직접 전달합니다.
        return webClient.get()
                .uri(uri) // .uri() 메소드는 URI 객체도 받을 수 있습니다.
                .header("X-Naver-Client-Id", naverClientId)
                .header("X-Naver-Client-Secret", naverClientSecret)
                .retrieve()
                .bodyToMono(NaverShoppingResponse.class)
                .block();
    }
    
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
    
    private InspectionResult handleException(Exception e) {
        // WebClientResponseException 타입인지 확인
        if (e instanceof WebClientResponseException webClientException) {
            // 응답 상태 코드와 본문을 로그로 남김
            String responseBody = webClientException.getResponseBodyAsString(StandardCharsets.UTF_8);
            log.error("외부 API 호출 오류 - Status: {}, Response Body: {}", 
                      webClientException.getStatusCode(), responseBody, webClientException);
            
            // 에러 메시지에 응답 내용 일부를 포함하여 반환
            return InspectionResult.reject(9, "AI 검수 서버 오류: " + responseBody, "exception");

        } else if (e instanceof IllegalArgumentException) {
            log.warn("가격 검증 실패: {}", e.getMessage());
            return InspectionResult.reject(9, e.getMessage(), "exception");
        } else if (e instanceof IOException) {
            log.error("파일 처리 중 오류 발생", e);
            return InspectionResult.reject(9, "파일을 처리하는 중 오류가 발생했습니다.", "exception");
        } else {
            log.error("알 수 없는 오류 발생", e);
            return InspectionResult.reject(9, "AI 검수 중 알 수 없는 오류가 발생했습니다.", "exception");
        }
    }
}