package com.tikitaka.api.category;

import com.tikitaka.api.category.entity.Category;
import com.tikitaka.api.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * level, code, parentCode를 파라미터로 받아 카테고리 목록을 조회하는 API
     * @param level 카테고리 레벨
     * @param code 카테고리 코드
     * @param parentCode 부모 카테고리 코드
     * @return 조회된 카테고리 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<Category>>> getCategoriesBy(
            @RequestParam(name="level", required=true) int level,
            @RequestParam(name="code", required=false) String[] code,
            @RequestParam(name="parentCode", required=false) String parentCode) {
        
        List<Category> categories = categoryService.getCategories(level, code, parentCode);
        return ResponseEntity.ok(ApiResponseDto.success("카테고리 목록을 성공적으로 조회했습니다.", categories));
    }
}