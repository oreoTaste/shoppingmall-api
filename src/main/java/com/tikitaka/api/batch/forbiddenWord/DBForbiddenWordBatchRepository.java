package com.tikitaka.api.batch.forbiddenWord;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DBForbiddenWordBatchRepository implements ForbiddenWordBatchRepository {

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
            "AND end_date >= CURRENT_DATE"
        );

        // 동적 파라미터를 관리할 리스트
        List<Object> params = new ArrayList<>();

        // 검색 조건 추가
        if (searchParam.getLgroup() != null && !searchParam.getLgroup().isEmpty()) {
            sqlBuilder.append(" AND (lgroup is null OR lgroup = ?)");
            params.add(searchParam.getLgroup());
        }
        if (searchParam.getMgroup() != null && !searchParam.getMgroup().isEmpty()) {
            sqlBuilder.append(" AND (mgroup is null OR mgroup = ?)");
            params.add(searchParam.getMgroup());
        }
        if (searchParam.getSgroup() != null && !searchParam.getSgroup().isEmpty()) {
            sqlBuilder.append(" AND (sgroup is null OR sgroup = ?)");
            params.add(searchParam.getSgroup());
        }
        if (searchParam.getDgroup() != null && !searchParam.getDgroup().isEmpty()) {
            sqlBuilder.append(" AND (dgroup is null OR dgroup = ?)");
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
	 * 금칙어 목록 전체를 데이터베이스와 동기화합니다. (Batch Upsert + Deactivate)
	 * 1. 목록에 있는 데이터는 MERGE (INSERT or UPDATE)를 수행합니다.
	 * 2. 목록에 없는 데이터는 비활성화 (endDate를 어제로) 처리합니다.
	 * @param forbiddenWords 동기화할 전체 금칙어 목록
	 * @return 성공 여부 (현재 로직에서는 예외가 없으면 true 반환)
	 */
	@Override
	public boolean saveAll(List<ForbiddenWord> forbiddenWords) {
		// 0. 배치 기준 시간을 "데이터베이스"에서 조회 (Clock Skew 문제 해결)
		Timestamp batchStartTime = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        
        // 1. MERGE 문 수정
		// createdAt, updatedAt에 ? 파라미터를 사용하도록 변경
		String mergeSql = """
        MERGE INTO forbidden_words T
        USING (
            SELECT
                ? AS word, ? AS company_code,
                ? AS lgroup, ? AS mgroup, ? AS sgroup, ? AS dgroup,
                ? AS start_date, ? AS end_date, ? AS reason,
                ? :: timestamp AS p_timestamp
        ) S
		ON (
		    T.word = S.word AND
		    T.lgroup IS NOT DISTINCT FROM S.lgroup AND
		    T.mgroup IS NOT DISTINCT FROM S.mgroup AND
		    T.sgroup IS NOT DISTINCT FROM S.sgroup AND
		    T.dgroup IS NOT DISTINCT FROM S.dgroup AND
		    T.end_date > CURRENT_DATE AND
		    T.start_date <= current_date
		)
		WHEN MATCHED THEN
		    UPDATE SET
		        end_date = S.end_date:: date,
		        reason = S.reason,
		        updated_at = S.p_timestamp
		WHEN NOT MATCHED THEN
		    INSERT (
		        word, company_code, lgroup, mgroup, sgroup, dgroup,
		        start_date, end_date, reason, created_at, updated_at
		    )
		    VALUES (
		        S.word, S.company_code, S.lgroup, S.mgroup, S.sgroup, S.dgroup,
		        S.start_date:: date, S.end_date:: date, S.reason,
		        S.p_timestamp, S.p_timestamp
		    )
        """;

		// 1-2. 배치 실행을 위한 파라미터 리스트 생성 (batchStartTime 추가)
        List<Object[]> batchArgs = forbiddenWords.stream()
                .map(word -> new Object[]{
                        word.getWord(),
                        word.getCompanyCode(),
                        word.getLgroup(),
                        word.getMgroup(),
                        word.getSgroup(),
                        word.getDgroup(),
                        batchStartTime.toLocalDateTime().toLocalDate() /*start_date*/,
                        LocalDate.of(9999, 12, 31) /*end_date*/,
                        word.getReason(),
                        batchStartTime // 10번째 파라미터
                })
                .collect(Collectors.toList());

		// 1-3. 배치 실행 (Upsert)
		if (batchArgs != null && !batchArgs.isEmpty()) {
			jdbcTemplate.batchUpdate(mergeSql, batchArgs);
		}
		
		// 2. 누락된 데이터 비활성화 (Deactivate)
		// MERGE에서 사용한 'batchStartTime'보다 이전에 업데이트된 데이터를 비활성화
		String deactivateSql = """
        UPDATE forbidden_words
           SET end_date = CURRENT_DATE - INTERVAL '1' DAY
         WHERE updated_at < ?
           AND end_date >= CURRENT_DATE
           AND start_date <= CURRENT_DATE
        """;

		jdbcTemplate.update(deactivateSql, batchStartTime);

		return true;
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