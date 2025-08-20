package com.tikitaka.api.category;

import com.tikitaka.api.category.entity.Category;

import java.util.List;

public interface CategoryService {
    /**
     * 카테고리 정보 조회
     * @param level 카테고리 레벨
     * @param code 카테고리 코드
     * @param parentCode 부모 카테고리 코드
     * @return 조회된 카테고리 리스트
     */
    List<Category> getCategories(Integer level, String[] code, String parentCode);
}