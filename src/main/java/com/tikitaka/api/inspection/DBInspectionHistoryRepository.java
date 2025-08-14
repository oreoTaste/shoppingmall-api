package com.tikitaka.api.inspection;

import com.tikitaka.api.inspection.entity.InspectionHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

@Repository
@RequiredArgsConstructor
public class DBInspectionHistoryRepository implements InspectionHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public InspectionHistory save(InspectionHistory history) {
        String sql = "INSERT INTO inspection_history " +
                     "(goods_id, inspection_date, is_approved, failure_code, failure_reason, inspector_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, history.getGoodsId());
            ps.setTimestamp(2, Timestamp.from(history.getInspectionDate().toInstant()));
            ps.setString(3, history.getIsApproved());
            ps.setInt(4, history.getErrorCode());
            ps.setString(5, history.getReason());
            ps.setString(6, history.getInspectorId());
            return ps;
        }, keyHolder);

        return history;
    }
}