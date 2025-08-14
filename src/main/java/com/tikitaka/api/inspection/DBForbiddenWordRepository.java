package com.tikitaka.api.inspection;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DBForbiddenWordRepository implements ForbiddenWordRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> findActiveForbiddenWords() {
        // 현재 날짜(CURRENT_DATE)를 기준으로 시작일이 지났고, 종료일이 아직 오지 않았거나(null) 지나지 않은 금칙어들을 조회
        String sql = "SELECT word " +
                     "FROM forbidden_words " +
                     "WHERE start_date <= CURRENT_DATE " +
                     "AND (end_date IS NULL OR end_date >= CURRENT_DATE)";

        // queryForList 메서드를 사용하면 특정 컬럼의 값들만 간단하게 리스트로 가져올 수 있습니다.
        return jdbcTemplate.queryForList(sql, String.class);
    }
}