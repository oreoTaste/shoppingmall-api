package com.tikitaka.api.batch.goods;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;

import java.sql.PreparedStatement;
import java.util.List;

@Primary
@Repository
@RequiredArgsConstructor
public class DBGoodsBatchRequestRepository implements GoodsBatchRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void saveAll(List<GoodsBatchRequest> requests) {
        String sql = "INSERT INTO goods_batch_request (" +
                     "batch_job_id, status, goods_name, mobile_goods_name, sales_price, buy_price, origin, image_html, " +
                     "representative_file_path, image_files_paths, " +
                     "lgroup, lgroup_name, mgroup, mgroup_name, sgroup, sgroup_name, dgroup, dgroup_name" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, requests, 100,
                (PreparedStatement ps, GoodsBatchRequest request) -> {
                    ps.setString(1, request.getBatchJobId());
                    ps.setString(2, "PENDING");
                    ps.setString(3, request.getGoodsName());
                    ps.setString(4, request.getMobileGoodsName());
                    ps.setBigDecimal(5, request.getSalesPrice());
                    ps.setBigDecimal(6, request.getBuyPrice());
                    ps.setString(7, request.getOrigin());
                    ps.setString(8, request.getImageHtml());
                    ps.setString(9, request.getRepresentativeFilePath());
                    ps.setString(10, request.getImageFilesPaths());
                    ps.setString(11, request.getLgroup());
                    ps.setString(12, request.getLgroupName());
                    ps.setString(13, request.getMgroup());
                    ps.setString(14, request.getMgroupName());
                    ps.setString(15, request.getSgroup());
                    ps.setString(16, request.getSgroupName());
                    ps.setString(17, request.getDgroup());
                    ps.setString(18, request.getDgroupName());
                });
    }

    /**
     * [수정된 부분]
     * ResultSet의 모든 컬럼을 GoodsBatchRequest 객체로 매핑합니다.
     */
    private final RowMapper<GoodsBatchRequest> rowMapper = (rs, rowNum) -> GoodsBatchRequest.builder()
            .requestId(rs.getLong("request_id"))
            .batchJobId(rs.getString("batch_job_id"))
            .status(rs.getString("status"))
            .goodsName(rs.getString("goods_name"))
            .mobileGoodsName(rs.getString("mobile_goods_name"))
            .salesPrice(rs.getBigDecimal("sales_price"))
            .buyPrice(rs.getBigDecimal("buy_price"))
            .origin(rs.getString("origin"))
            .imageHtml(rs.getString("image_html"))
            .representativeFilePath(rs.getString("representative_file_path"))
            .imageFilesPaths(rs.getString("image_files_paths"))
            .lgroup(rs.getString("lgroup"))
            .lgroupName(rs.getString("lgroup_name"))
            .mgroup(rs.getString("mgroup"))
            .mgroupName(rs.getString("mgroup_name"))
            .sgroup(rs.getString("sgroup"))
            .sgroupName(rs.getString("sgroup_name"))
            .dgroup(rs.getString("dgroup"))
            .dgroupName(rs.getString("dgroup_name"))
            .build();


    @Override
    public List<GoodsBatchRequest> findPendingRequests(int limit) {
        String sql = "SELECT * FROM goods_batch_request WHERE status = 'PENDING' ORDER BY request_id ASC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, limit);
    }

    @Override
    public void updateStatusToProcessing(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String sql = "UPDATE goods_batch_request SET status = 'PROCESSING', updated_at = NOW() WHERE request_id = ?";
        jdbcTemplate.batchUpdate(sql, ids, 100, (ps, id) -> ps.setLong(1, id));
    }

    @Override
    public void updateFinalStatus(Long requestId, String status, String inspectionStatus, String errorMessage) {
        String sql = "UPDATE goods_batch_request SET status = ?, inspection_status = ?, error_message = ?, updated_at = NOW() WHERE request_id = ?";
        jdbcTemplate.update(sql, status, inspectionStatus, errorMessage, requestId);
    }
}