package com.tikitaka.api.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.api.category.entity.Category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor // final 필드에 대한 생성자를 자동으로 생성합니다.
public class CategoryServiceImpl implements CategoryService {
	private final CategoryRepository categoryRepository;

	@Override
	public List<Category> getCategories(Integer level, String[] code, String parentCode) {
		// Repository에 파라미터를 전달하여 메소드를 호출합니다.
		return this.categoryRepository.findAll(level, code, parentCode);
	}
}