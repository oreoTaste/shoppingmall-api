package com.tikitaka.api.batch.inspection;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.batch.goods.entity.Goods;
import com.tikitaka.api.batch.inspection.dto.FileContent;
import com.tikitaka.api.batch.inspection.dto.InspectionResult;

public interface InspectBatchService {
    
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다.
     * @param goods 검수 대상 상품
     * @param files 검수용 이미지 파일
     * @return 검수 결과
     * @throws IOException 파일 처리 오류
     */
	public InspectionResult performAiInspection(Goods goods, MultipartFile[] files, String forbiddenWords) throws Exception;
    
    /**
     * AI 모델에 특화된 실제 검수 로직을 수행합니다. (오버로딩)
     * @param goods 검수 대상 상품
     * @param fileContents 검수용 기존 이미지 파일 정보
     * @return 검수 결과
     */
    public InspectionResult performAiInspection(Goods goods, List<FileContent> fileContents, String forbiddenWords) throws Exception;
    
}