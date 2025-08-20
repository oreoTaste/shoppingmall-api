package com.tikitaka.api.category;

import com.tikitaka.api.category.entity.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Repository
public class DBCategoryRepository implements CategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    // 샘플과 동일하게 DataSource를 주입받아 JdbcTemplate을 생성합니다.
    public DBCategoryRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public List<Category> findAll(Integer level, String[] code, String parentCode) {
        // 파라미터를 순서대로 담을 List를 생성합니다.
        List<Object> params = new ArrayList<>();
        
        StringBuilder sqlBuilder = new StringBuilder("SELECT category_id, level, code, name, parent_code FROM category WHERE 1=1");

        if (level != null) {
            sqlBuilder.append(" AND level = ?");
            params.add(level);
        }

        // === IN 절을 처리하는 핵심 로직 ===
        if (code != null && code.length > 0) {
            // 1. code 배열의 크기만큼 (?,?,?) 문자열을 만듭니다.
            String inClause = String.join(",", Collections.nCopies(code.length, "?"));
            
            // 2. SQL에 IN 절을 추가합니다.
            sqlBuilder.append(" AND code IN (").append(inClause).append(")");
            
            // 3. 파라미터 List에 code 배열의 모든 값을 추가합니다.
            params.addAll(Arrays.asList(code));
        }

        if (parentCode != null && !parentCode.isEmpty()) {
            sqlBuilder.append(" AND parent_code = ?");
            params.add(parentCode);
        }

        sqlBuilder.append(" ORDER BY code");

        // jdbcTemplate.query에 SQL과 RowMapper, 그리고 파라미터 배열을 전달합니다.
        return jdbcTemplate.query(sqlBuilder.toString(), categoryRowMapper(), params.toArray());
    }

    private RowMapper<Category> categoryRowMapper() {
        return (rs, rowNum) -> {
            Category category = new Category();
            category.setCategoryId(rs.getLong("category_id"));
            category.setLevel(rs.getInt("level"));
            category.setCode(rs.getString("code"));
            category.setName(rs.getString("name"));
            category.setParentCode(rs.getString("parent_code"));
            return category;
        };
    }
}