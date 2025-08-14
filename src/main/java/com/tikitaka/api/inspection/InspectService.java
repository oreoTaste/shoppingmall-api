package com.tikitaka.api.inspection;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;

public interface InspectService {
    
    /**
     * 새로 업로드된 파일들을 사용하여 상품 정보를 검수합니다.
     * @param goods 검수할 상품 정보
     * @param files 새로 첨부된 파일 배열
     * @return 검수 결과
     */
    InspectionResult inspectGoodsInfoWithPhotos(Goods goods, MultipartFile[] files, String forbiddenWords);

    /**
     * DB에서 읽어온 기존 파일들을 사용하여 상품 정보를 검수합니다.
     * @param goods 검수할 상품 정보
     * @param fileContents 기존 파일들의 내용이 담긴 DTO 리스트
     * @return 검수 결과
     */
    InspectionResult inspectGoodsInfoWithPhotos(Goods goods, List<FileContent> fileContents, String forbiddenWords);
	
    
}