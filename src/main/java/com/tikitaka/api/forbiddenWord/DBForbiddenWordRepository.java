package com.tikitaka.api.forbiddenWord;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DBForbiddenWordRepository implements ForbiddenWordRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 모든 활성 금칙어 목록을 조회합니다.
     * @return ForbiddenWord 객체 리스트
     */
    @Override
    public List<ForbiddenWord> findActiveForbiddenWords() {
        String sql = "SELECT * FROM forbidden_words " +
                     "WHERE start_date <= CURRENT_DATE " +
                     "AND (end_date IS NULL OR end_date >= CURRENT_DATE)";
        return jdbcTemplate.query(sql, forbiddenWordRowMapper());
    }

    /**
     * 검색 조건에 맞는 활성 금칙어 목록을 동적으로 조회합니다.
     * @param searchParam 검색 조건을 담은 객체
     * @return ForbiddenWord 객체 리스트
     */
    @Override
    public List<ForbiddenWord> findActiveForbiddenWords(ForbiddenWordSearchParam searchParam) {
        // 기본 SQL 쿼리
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT * FROM forbidden_words " +
            "WHERE start_date <= CURRENT_DATE " +
            "AND (end_date IS NULL OR end_date >= CURRENT_DATE)"
        );

        // 동적 파라미터를 관리할 리스트
        List<Object> params = new ArrayList<>();

        // 검색 조건 추가
        if (searchParam.getLgroup() != null && !searchParam.getLgroup().isEmpty()) {
            sqlBuilder.append(" AND lgroup = ?");
            params.add(searchParam.getLgroup());
        }
        if (searchParam.getMgroup() != null && !searchParam.getMgroup().isEmpty()) {
            sqlBuilder.append(" AND mgroup = ?");
            params.add(searchParam.getMgroup());
        }
        if (searchParam.getSgroup() != null && !searchParam.getSgroup().isEmpty()) {
            sqlBuilder.append(" AND sgroup = ?");
            params.add(searchParam.getSgroup());
        }
        if (searchParam.getDgroup() != null && !searchParam.getDgroup().isEmpty()) {
            sqlBuilder.append(" AND dgroup = ?");
            params.add(searchParam.getDgroup());
        }
        if (searchParam.getWord() != null && !searchParam.getWord().isEmpty()) {
            sqlBuilder.append(" AND word LIKE ?");
            params.add("%" + searchParam.getWord() + "%");
        }

	    // Corrected the order of arguments to use the non-deprecated method
	    return jdbcTemplate.query(sqlBuilder.toString(), forbiddenWordRowMapper(), params.toArray());
	}
    
    @Override
    public boolean save(ForbiddenWord forbiddenWord) {
        // SQL 쿼리에서 하드코딩된 값을 모두 플레이스홀더(?)로 변경
        String sql = "INSERT INTO forbidden_words (word, start_date, end_date, reason, company_code, lgroup, mgroup, sgroup, dgroup) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        // jdbcTemplate.update에 ForbiddenWord 객체의 모든 필드 값을 전달
        int updatedRows = jdbcTemplate.update(sql,
                forbiddenWord.getWord(),
                forbiddenWord.getStartDate(),
                forbiddenWord.getEndDate(),
                forbiddenWord.getReason(),
		        forbiddenWord.getCompanyCode(),
                forbiddenWord.getLgroup(),
		        forbiddenWord.getMgroup(),
		        forbiddenWord.getSgroup(),
		        forbiddenWord.getDgroup());
                
        return updatedRows == 1;
    }

    /**
     * Deactivates a forbidden word by setting its end_date to yesterday.
     * This method performs a "soft delete".
     * @param id The ID of the forbidden word to deactivate.
     * @return true if the word was successfully deactivated, false otherwise.
     */
    @Override
    public boolean deactivateById(Long id) {
        // Safety check: ensure an ID is provided.
        if (id == null) {
            return false;
        }

        String sql = "UPDATE forbidden_words SET end_date = CURRENT_DATE - INTERVAL '1 day' WHERE forbidden_word_id = ?";

        int updatedRows = jdbcTemplate.update(sql, id);
        
        // Return true if exactly one row was updated.
        return updatedRows == 1;
    }


    /**
     * ResultSet의 한 행을 ForbiddenWord 객체로 매핑하는 RowMapper입니다.
     * @return RowMapper<ForbiddenWord>
     */
    private RowMapper<ForbiddenWord> forbiddenWordRowMapper() {
        return (rs, rowNum) -> {
            ForbiddenWord forbiddenWord = new ForbiddenWord();
            forbiddenWord.setForbiddenWordId(rs.getLong("forbidden_word_id"));
            forbiddenWord.setWord(rs.getString("word"));
            forbiddenWord.setCompanyCode(rs.getString("company_code"));
            forbiddenWord.setStartDate(rs.getDate("start_date"));
            forbiddenWord.setEndDate(rs.getDate("end_date"));
            forbiddenWord.setReason(rs.getString("reason"));
            forbiddenWord.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            forbiddenWord.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
            forbiddenWord.setLgroup(rs.getString("lgroup"));
            forbiddenWord.setMgroup(rs.getString("mgroup"));
            forbiddenWord.setSgroup(rs.getString("sgroup"));
            forbiddenWord.setDgroup(rs.getString("dgroup"));
            return forbiddenWord;
        };
    }
}