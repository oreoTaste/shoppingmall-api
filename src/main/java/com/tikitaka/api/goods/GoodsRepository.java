package com.tikitaka.api.goods;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;

/**
 * Goods 데이터베이스 작업을 위한 Repository 인터페이스.
 * 상품 데이터에 대한 CRUD (Create, Read, Update, Delete) 작업을 정의합니다.
 */
@Repository()
public interface GoodsRepository {
    /**
     * 새로운 상품을 저장합니다.
     * @param goods 저장할 Goods 객체
     * @return 저장된 Goods 객체 (goodsId가 포함될 수 있음)
     */
    Goods save(Goods goods);


    /**
     * 상품 정보를 업데이트합니다.
     * @param goods 업데이트할 Goods 객체 (goodsId가 반드시 포함되어야 함)
     * @return 업데이트 성공 여부
     */
    boolean update(Goods goods);

    /**
     * 주어진 goodsId의 상품을 삭제합니다.
     * @param goodsId 삭제할 상품의 ID
     * @return 삭제 성공 여부
     */
    boolean delete(Long goodsId);

	/**
	 * 기간별 상품 조회를 위한 메서드입니다. 현재는 findAll()과 동일하게 모든 상품을 반환합니다.
	 * 추후 필요에 따라 `WHERE insert_at BETWEEN ? AND ?`와 같은 조건 추가 가능.
	 * @return 모든 상품의 리스트 (현재는 findAll()과 동일)
	 */
	List<GoodsListDto> findAllbyPeriodWithFiles();


	/**
	 * 기간별 상품 조회를 위한 메서드입니다. 현재는 findOne()과 동일하게 모든 상품을 반환합니다.
	 * 추후 필요에 따라 `WHERE insert_at BETWEEN ? AND ?`와 같은 조건 추가 가능.
	 * @return 모든 상품의 리스트 (현재는 findOne()과 동일)
	 */
	GoodsListDto findbyPeriodWithFiles(Long goodsId);


    /**
     * 주어진 goodsId로 상품을 조회합니다.
     * @param goodsId 조회할 상품의 ID
     * @return 조회된 Goods 객체, 없으면 null 반환
     */
    Goods findById(Long goodsId);
}