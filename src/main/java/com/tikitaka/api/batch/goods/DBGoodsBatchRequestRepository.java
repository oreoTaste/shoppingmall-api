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
                     "batch_job_id, status, goods_code, goods_name, mobile_goods_name, sale_price, buy_price, goods_info, image_html, " +
                     "representative_file, " +
                     "lgroup, lgroup_name, mgroup, mgroup_name, sgroup, sgroup_name, dgroup, dgroup_name" +
                     ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, requests, 100,
                (PreparedStatement ps, GoodsBatchRequest request) -> {
                    ps.setString(1, request.getBatchJobId());
                    ps.setString(2, "PENDING");
                    ps.setString(3, request.getGoodsCode());
                    ps.setString(4, request.getGoodsName());
                    ps.setString(5, request.getMobileGoodsName());
                    ps.setBigDecimal(6, request.getSalePrice());
                    ps.setBigDecimal(7, request.getBuyPrice());
                    ps.setString(8, request.getGoodsInfo());
                    ps.setString(9, request.getImageHtml());
                    ps.setString(10, request.getRepresentativeFile());
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
     * ResultSet의 모든 컬럼을 GoodsBatchRequest 객체로 매핑합니다.
     */
    private final RowMapper<GoodsBatchRequest> rowMapper = (rs, rowNum) -> GoodsBatchRequest.builder()
            .requestId(rs.getLong("request_id"))
            .batchJobId(rs.getString("batch_job_id"))
            .status(rs.getString("status"))
            .goodsCode(rs.getString("goods_code"))
            .goodsName(rs.getString("goods_name"))
            .mobileGoodsName(rs.getString("mobile_goods_name"))
            .salePrice(rs.getBigDecimal("sale_price"))
            .buyPrice(rs.getBigDecimal("buy_price"))
            .goodsInfo(rs.getString("goods_info"))
            .imageHtml(rs.getString("image_html"))
            .representativeFile(rs.getString("representative_file"))
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
        String sql = "SELECT * FROM goods_batch_request WHERE status = 'PENDING' ORDER BY goods_code ASC LIMIT ?";
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

    @Override
    public List<String> findOldBatchRecord(int day) {
        String sql = "select a.batch_job_id"
        			+"  from goods_batch_request a"
        			+" WHERE a.created_at <= NOW() - make_interval(days => ?)"
        			+"  and not exists (select 1"
        			+"                    from goods_batch_request aa"
        			+"                   where aa.batch_job_id = a.batch_job_id"
        			+"                     and aa.created_at >  NOW() - make_interval(days => ?))";
        return jdbcTemplate.queryForList(sql, String.class, day, day);
    }

    @Override
    public void deleteOldBatchRecord(int day) {
        String sql = "DELETE from goods_batch_request a "
        			+ "WHERE a.created_at <= NOW() - make_interval(days => ?)"
        			+ "  and not exists (select 1"
        			+ "                    from goods_batch_request aa"
        			+ "                   where aa.batch_job_id = a.batch_job_id"
        			+ "                     and aa.created_at >  NOW() - make_interval(days => ?))";
        jdbcTemplate.update(sql, day, day);
    }
}