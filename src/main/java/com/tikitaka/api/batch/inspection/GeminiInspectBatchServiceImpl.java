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
                log.debug("파일명 : {}", fileName);
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
            	// 파일 내용이 없으면(0 byte) 건너뛰는 방어 로직
                if (file.getContent() == null || file.getContent().length == 0) {
                    log.warn("파일 크기가 0이므로 전송에서 제외합니다. 파일명: {}", file.getOriginalFileName());
                    continue;
                }
                
            	// 1. 파일명 정보를 텍스트 파트로 먼저 추가
                //    Gemini에게 "이 이미지는 [파일명]입니다"라고 알려주는 역할을 합니다.
                String fileName = String.format("--- 첨부 이미지 파일명: %s ---", file.getOriginalFileName());
                log.debug("파일명 : {}", fileName);
                imageParts.add(new GeminiRequest.Part(fileName)); 

                // 2. [디버깅 로직] 실제 전송되는 MIME Type 로그 확인
                log.debug("Gemini 전송 파일 정보 - 이름: {}, MIME: {}, 크기: {} bytes", 
                         file.getOriginalFileName(), file.getMimeType(), file.getContent().length);

                // 3. [방어 로직] MIME Type이 octet-stream이거나 null이면 강제 변환
                String mimeType = file.getMimeType();
                if (mimeType == null || "application/octet-stream".equals(mimeType)) {
                     mimeType = "image/jpeg"; // Gemini가 인식 가능한 타입으로 강제 설정
                     log.warn("파일({})의 MIME Type이 불명확({})하여 image/jpeg로 강제 변환합니다.", file.getOriginalFileName(), file.getMimeType());
                }

                // 4. 이미지 데이터 파트 추가
                String base64EncodedImage = Base64.getEncoder().encodeToString(file.getContent());
                imageParts.add(new GeminiRequest.Part(new GeminiRequest.InlineData(mimeType, base64EncodedImage)));
            }
        }
        return imageParts;
    }

    private GeminiRequest createGeminiRequest(Goods goods, List<GeminiRequest.Part> imageParts, String forbiddenWords) {
        List<GeminiRequest.Part> parts = new ArrayList<>();
        parts.add(new GeminiRequest.Part(createPromptForCheckForbiddenWords(goods, forbiddenWords)));
        parts.addAll(imageParts);
        
        List<GeminiRequest.Content> contents = List.of(new GeminiRequest.Content(parts));
        
        // [안전 설정 추가]
        List<GeminiRequest.SafetySetting> safetySettings = new ArrayList<>();
        // 선정성 필터 해제 (가장 중요)
        safetySettings.add(new GeminiRequest.SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"));
        // 기타 필터 해제
        safetySettings.add(new GeminiRequest.SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"));
        safetySettings.add(new GeminiRequest.SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"));
        safetySettings.add(new GeminiRequest.SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE"));

        // 생성자를 통해 safetySettings 전달
        return new GeminiRequest(contents, safetySettings);
    }
    
    private GeminiResponse callGeminiApi(GeminiRequest requestBody) {
        String urlTemplate = geminiApiUrl + "/v1beta/models/{modelName}:generateContent?key={apiKey}";
        Map<String, String> uriVariables = Map.of(
                "modelName", geminiModelName,
                "apiKey", geminiApiKey
        );
        
        try {
            return webClient.post()
                    .uri(urlTemplate, uriVariables)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Gemini API 호출 중 오류 발생 - Status: {}, Response Body: {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(StandardCharsets.UTF_8));
            throw e;
        }
    }


    private InspectionResult parseGeminiResponse(GeminiResponse response) {
    	if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
            log.info("Gemini 응답 상세 확인: {}", response); 
        }

    	// 0. 안전 필터 등에 의해 차단되었는지 우선 확인
        if (response != null && response.getPromptFeedback() != null && response.getPromptFeedback().getBlockReason() != null) {
            String blockReason = response.getPromptFeedback().getBlockReason();
            log.warn("Gemini가 안전 설정에 의해 응답을 차단했습니다. 사유: {}", blockReason);
            // 차단된 경우 '실패' 또는 '반려'로 처리 (여기서는 에러 메시지와 함께 반려 처리 예시)
            return InspectionResult.reject(null, "AI 안전 정책에 의해 차단되었습니다 (사유: " + blockReason + ")", geminiModelName);
        }
    	
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
            return InspectionResult.reject(null, "AI가 판독 불가 응답을 반환했습니다: ", geminiModelName);
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
				- 검수 대상 이미지에는 **상업용/전자상거래용 상품사진**이 제공된다.
				
				### 금칙어 목록
				- %s
				
				### 수행 과업
				1.  **[이미지 전처리 & 텍스트 추출]**
				    - 제공된 이미지 내에서 **시각적 요소(사람, 사물, 풍경 등)는 완전히 배제**하고, 오직 **텍스트(글자)**만을 추출하여 인식하라.
				    - 그림(소방관, 경찰차, 무기 등)의 내용은 **절대 판단하지 말고**, 오직 이미지 내 **글자**만 추출한다.
				    - 텍스트 식별이 불가능할 정도로 **흐릿하거나 해상도가 낮은 이미지**는 분석 대상에서 **제외(승인)**한다.
				
				2.  **[금칙어 및 변형 탐지 기준]**
				    추출된 텍스트가 아래 **세 가지 경우(A, B, C)** 중 하나라도 해당하면 즉시 '반려' 처리한다.
				
				    - **A. 직접 포함 (Substring Match):**
				        - 텍스트 안에 금칙어가 **그대로 포함**되어 있는 경우.
				        - 합성어 포함 (예: 금칙어 '군복' -> '구형군복', '군복바지' 등은 **반려**)
				    - **B. 형태적 변형 (Visual Match):**
				        - 자음/모음 분리, 특수문자 삽입, 야민정음 등 **글자 모양이 유사**한 경우.
				        - (예: '병신' -> 'ㅂㅅ', '병!신', '뼝신')
				    - **C. 발음적 변형 (Phonetic Match):**
				        - 소리 내어 읽었을 때 발음이 매우 유사한 경우.
				        - (예: '병신' -> '병쉰', '븅신')
				
				3.  **[탐지 제외 기준 - 환각(Hallucination) 방지]**
				    위의 A, B, C에 해당하지 않는다면, 문맥상 의미가 통해도 **절대 잡지 않는다.**
				    - **유의어 금지:** 글자와 발음이 다르면 뜻이 같아도 승인한다. (예: '군복' 금칙어 -> '밀리터리' 승인)
				    - **이미지 억지 연결 금지:** 이미지에서 인식된 텍스트가 금칙어와 철자가 확연히 다르면 승인한다.
				        - (실패 사례 방지: '경찰' <-> '망사하프덧신', '군복' <-> 'CAM', 'POLICE' <-> 'POF PORT' 등은 모두 **승인**)
				
				4.  검사 순서: 등록 상품명 -> 모바일용 상품명 -> 기타 공시사항 -> 이미지 내 텍스트
				
				### 출력 규칙
				- **규칙 1 (승인):**
				    - 금칙어(또는 명확한 변형)가 발견되지 않은 경우
				    - 발견된 단어가 금칙어와 의미만 비슷하고 글자는 다른 경우(유의어)
				    -> 위 경우에는 오직 **'승인'** 이라고만 응답한다.
				
				- **규칙 2 (반려):**
				    - 금칙어 또는 [탐지 기준 A, B, C]에 해당하는 단어가 발견되면, **가장 먼저 발견된 하나**만 출력한다.
				    - 형식: `반려:[원본 금칙어]:[사유]`
				    - [사유]: `[발견 위치]에서 금칙어 '[원본 금칙어]'의 변형 표현('[발견된 표현]') 발견`
				    - [발견 위치]: '등록 상품명', '모바일용 상품명', '기타 공시사항', '이미지' 중 택 1.
				
				- **규칙 3:** 설명, 인사, 사과 등 불필요한 텍스트 금지.
				
				### 출력 예시
				- **(상황: 텍스트에 '군용조끼'가 있고 금칙어가 '군용'인 경우 - A.직접 포함)**
				반려:군용:등록 상품명에서 금칙어 '군용'의 변형 표현('군용조끼') 발견
				- **(상황: 이미지에 '망사하프덧신'이 있고 금칙어가 '경찰'인 경우 - 환각 방지)**
				승인
				- **(상황: 상품명에 '밀리터리 룩'이 있고 금칙어가 '군복'인 경우 - 유의어 제외)**
				승인
				- **(상황: 이미지에 'Po!ice'가 있고 금칙어가 'POLICE'인 경우 - B.형태적 변형)**
				반려:POLICE:이미지에서 금칙어 'POLICE'의 변형 표현('Po!ice') 발견
				
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