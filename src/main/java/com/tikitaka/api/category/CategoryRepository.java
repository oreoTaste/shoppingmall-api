package com.tikitaka.api.category;

import com.tikitaka.api.category.entity.Category;

import java.util.List;

public interface CategoryRepository {
    /**
     * level, code, parentCode를 기준으로 카테고리 목록을 조회합니다.
     * @param level 카테고리 레벨
     * @param code 카테고리 코드
     * @param parentCode 부모 카테고리 코드
     * @return 조회된 카테고리 리스트
     */
    List<Category> findAll(Integer level, String[] code, String parentCode);
}