package com.tikitaka.api.files;

import com.tikitaka.api.files.entity.Files;

import java.util.List;
import java.util.Optional;

public interface FilesRepository {
    /**
     * 새로운 파일 정보를 데이터베이스에 저장합니다.
     * @param file 저장할 Files 객체
     * @return DB에 저장되고 files_id가 설정된 Files 객체
     */
    Files save(Files file);

    /**
     * filesId를 사용하여 단일 파일 정보를 조회합니다.
     * @param filesId 조회할 파일의 ID
     * @return 조회된 파일 정보가 존재하면 Optional<Files>, 없으면 Optional.empty()
     */
    Optional<Files> findById(Long filesId);

    /**
     * goodsId를 사용하여 해당 상품에 연관된 모든 파일 목록을 조회합니다.
     * @param goodsId 조회할 상품의 ID
     * @return 조회된 파일 정보 리스트
     */
    List<Files> findByGoodsId(Long goodsId);

    /**
     * goodsId를 사용하여 해당 상품에 연관된 모든 파일 목록을 삭제합니다.
     * @param goodsId 조회할 상품의 ID
     * @return 성공여부
     */
	boolean deleteByGoodsId(Long goodsId);

}
