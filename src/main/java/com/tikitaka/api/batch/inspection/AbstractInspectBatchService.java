package com.tikitaka.api.batch.inspection;

import com.tikitaka.api.batch.goods.entity.Goods;
import com.tikitaka.api.batch.inspection.dto.FileContent;
import com.tikitaka.api.batch.inspection.dto.InspectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.io.IOException;
import java.util.List;

@Slf4j
public abstract class AbstractInspectBatchService implements InspectBatchService {

    protected final WebClient webClient;

    /**
     * 공통으로 필요한 의존성을 주입받는 생성자
     */
    public AbstractInspectBatchService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // --- 자식 클래스가 반드시 구현해야 할 핵심 메소드 ---
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다.
     * @param goods 검수 대상 상품
     * @param files 검수용 이미지 파일
     * @return 검수 결과
     * @throws IOException 파일 처리 오류
     */
    public abstract InspectionResult performAiInspection(Goods goods, MultipartFile[] files, String forbiddenWords) throws Exception;
    
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다. (오버로딩)
     * @param goods 검수 대상 상품
     * @param fileContents 검수용 기존 이미지 파일 정보
     * @return 검수 결과
     */
    public abstract InspectionResult performAiInspection(Goods goods, List<FileContent> fileContents, String forbiddenWords) throws Exception;
    
    // --- 공통 Private Helper Methods ---
    protected abstract String getInspectorId();
    
}