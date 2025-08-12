package com.tikitaka.api.goods;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.member.dto.CustomUserDetails;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스 인터페이스.
 * Repository 계층과 Controller/API 계층 사이에서 비즈니스 규칙을 적용하고 트랜잭션을 관리합니다.
 */
public interface GoodsService {
    /**
     * 새로운 상품을 저장합니다.
     * @param goods 저장할 Goods 객체
     * @return 저장된 Goods 객체 (goodsId가 포함될 수 있음)
     */
    Goods save(Goods goods);
        
    /**
     * 주어진 기간 동안의 모든 상품 목록을 반환합니다.
     * 현재는 findAll()과 동일하게 구현되지만, 필요에 따라 기간 필터링 로직을 추가할 수 있습니다.
     * @return 기간에 해당하는 상품의 리스트
     */
    List<GoodsListDto> findAllbyPeriodWithFiles();

    /**
     * 주어진 기간 동안의 특정 상품을 반환합니다.
     * 현재는 findOne()과 동일하게 구현되지만, 필요에 따라 기간 필터링 로직을 추가할 수 있습니다.
     * @return 기간에 해당하는 상품의 리스트
     */
    GoodsListDto findbyPeriodWithFiles(Long goodsId);

    
    /**
     * 주어진 goodsId의 상품을 삭제합니다.
     * @param goodsId 삭제할 상품의 ID
     * @return 삭제 성공 여부
     */
    boolean delete(Long goodsId);

    /**
     * 상품 정보와 파일을 함께 업데이트하는 트랜잭션 메소드
     */
    boolean updateWithFiles(Goods goods, MultipartFile[] representativeFiles,  MultipartFile[] imageFiles, CustomUserDetails userDetails) throws IOException;
    
    /**
     * 상품 정보와 파일을 함께 업데이트하는 트랜잭션 메소드
     */
    boolean updateWithFiles(Goods goods, MultipartFile[] representativeFiles, String imageTag, CustomUserDetails userDetails) throws IOException;
    
    /**
     * AI검토 완료여부값을 update하는 메소드.
     */
    boolean updateAiCheckYn(Goods goods);
    
    
}
