package com.tikitaka.api.goods;

import javax.sql.DataSource;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder; // GeneratedKeyHolder 임포트 추가
import org.springframework.jdbc.support.KeyHolder; // KeyHolder 임포트 추가
import org.springframework.stereotype.Repository;

import com.tikitaka.api.files.dto.FilesCoreDto;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;

import java.sql.PreparedStatement; // PreparedStatement 임포트 추가
import java.sql.Statement; // Statement 임포트 추가
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet; // ResultSet 임포트 추가
import java.sql.SQLException; // SQLException 임포트 추가

/**
 * GoodsRepository 인터페이스의 JDBC 기반 구현체.
 * Spring의 JdbcTemplate을 사용하여 PostgreSQL 데이터베이스와 상호작용합니다.
 */
@Primary
@Repository()
public class DBGoodsRepository implements GoodsRepository {
    private final JdbcTemplate jdbcTemplate;

    /**
     * DataSource를 주입받아 JdbcTemplate을 초기화합니다.
     * @param dataSource 데이터베이스 연결을 위한 DataSource
     */
    public DBGoodsRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Goods 객체를 데이터베이스에 저장합니다.
     * goods_id는 DB에서 자동 생성되므로, 저장 후 생성된 ID를 Goods 객체에 설정하여 반환합니다.
     * @param goods 저장할 Goods 객체
     * @return DB에 저장되고 goodsId가 설정된 Goods 객체
     */
    @Override
    public Goods save(Goods goods) {
        // goods_id 자동 생성을 위한 KeyHolder
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO public.goods (goods_name, mobile_goods_name, sales_price, buy_price, origin, insert_id, update_id, ai_check_yn) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        jdbcTemplate.update(connection -> {
            // PreparedStatement 생성 시 Statement.RETURN_GENERATED_KEYS 옵션을 사용하여 자동 생성된 키를 반환받습니다.
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, goods.getGoodsName());
            ps.setString(2, goods.getMobileGoodsName());
            ps.setLong(3, goods.getSalesPrice());
            ps.setLong(4, goods.getBuyPrice());
            ps.setString(5, goods.getOrigin());
            ps.setLong(6, goods.getInsertId());
            ps.setLong(7, goods.getUpdateId());
            ps.setString(8, goods.getAiCheckYn());
            return ps;
        }, keyHolder);

        // 생성된 키(goods_id)를 Goods 객체에 설정
        if (keyHolder.getKeys() != null) {
            goods.setGoodsId(((Number) keyHolder.getKeys().get("goods_id")).longValue());
        }
        return goods;
    }


    /**
     * 상품 정보를 업데이트합니다.
     * modified_at 필드는 데이터베이스에서 NOW()로 자동 업데이트되도록 처리.
     * @param goods 업데이트할 Goods 객체
     * @return 업데이트된 행의 수가 1이면 true, 아니면 false
     */
    @Override
    public boolean update(Goods goods) {
        String sql = "UPDATE public.goods SET goods_name = ?, mobile_goods_name = ?, sales_price = ?, buy_price = ?, update_id = ?, modified_at = NOW() WHERE goods_id = ?";
        int affectedRows = jdbcTemplate.update(sql,
            goods.getGoodsName(),
            goods.getMobileGoodsName(),
            goods.getSalesPrice(),
            goods.getBuyPrice(),
            goods.getUpdateId(),
            goods.getGoodsId()
        );
        return affectedRows == 1;
    }

    /**
     * goodsId를 사용하여 상품을 삭제합니다.
     * @param goodsId 삭제할 상품의 ID
     * @return 삭제된 행의 수가 1이면 true, 아니면 false
     */
    @Override
    public boolean delete(Long goodsId) {
        String sql = "DELETE FROM public.goods WHERE goods_id = ?";
        int affectedRows = jdbcTemplate.update(sql, goodsId);
        return affectedRows == 1;
    }
    

    /**
     * [수정] 파일 정보를 포함하여 모든 상품을 조회합니다.
     * 1:N 관계를 처리하기 위해 RowMapper 대신 ResultSetExtractor를 사용합니다.
     */
    @Override
    public List<GoodsListDto> findAllbyPeriodWithFiles() {
        String sql = "SELECT " +
                     "    a.goods_id, a.goods_name, a.mobile_goods_name, a.sales_price, a.buy_price, " +
                     "    a.origin, a.insert_at, a.insert_id, a.modified_at, a.update_id, a.ai_check_yn, " +
                     "    b.files_id, b.file_path, b.file_name " + // files 테이블의 정보
                     "FROM public.goods a LEFT OUTER JOIN public.files b ON a.goods_id = b.goods_id " +
                     "ORDER BY a.goods_id ASC, b.files_id ASC"; // 정렬을 추가하여 그룹핑을 용이하게 합니다.
        
        return jdbcTemplate.query(sql, new GoodsWithFilesResultSetExtractor());
    }

    /**
     * [추가] 1-N 관계의 JOIN 결과를 올바르게 매핑하기 위한 ResultSetExtractor 구현체입니다.
     */
    private static class GoodsWithFilesResultSetExtractor implements ResultSetExtractor<List<GoodsListDto>> {
        @Override
        public List<GoodsListDto> extractData(ResultSet rs) throws SQLException, DataAccessException {
            // goodsId를 키로 사용하여 Goods 객체를 관리하는 맵
            Map<Long, GoodsListDto> goodsMap = new HashMap<>();

            while (rs.next()) {
                Long goodsId = rs.getLong("goods_id");
                // 맵에서 현재 goodsId에 해당하는 Goods 객체를 찾습니다.
                GoodsListDto goods = goodsMap.get(goodsId);

                // 맵에 없는 새로운 상품인 경우, Goods 객체를 생성하여 맵에 추가합니다.
                if (goods == null) {
                    goods = new GoodsListDto(
                        goodsId,
                        rs.getString("goods_name"),
                        rs.getString("mobile_goods_name"),
                        rs.getLong("sales_price"),
                        rs.getLong("buy_price"),
                        rs.getString("origin"),
                        rs.getLong("insert_id"),
                        rs.getLong("update_id")
                    );
                    goods.setInsertAt(rs.getDate("insert_at"));
                    goods.setModifiedAt(rs.getDate("modified_at"));
                    goods.setAiCheckYn(rs.getString("ai_check_yn"));
                    goodsMap.put(goodsId, goods);
                }

                // 파일 정보가 존재하는 경우 (LEFT JOIN으로 인해 null일 수 있음)
                if (rs.getObject("files_id") != null) {
                	FilesCoreDto file = new FilesCoreDto();
                    file.setFilesId(rs.getLong("files_id"));
                    file.setFilePath(rs.getString("file_path"));
                    file.setFileName(rs.getString("file_name"));
                    file.setGoodsId(goodsId); // 파일 객체에도 goodsId를 설정
                    
                    // 해당 상품의 파일 리스트에 생성된 파일 객체를 추가합니다.
                    goods.getFiles().add(file);
                }
            }
            // 맵에 저장된 모든 GoodsListDto 객체를 리스트로 변환하여 반환합니다.
            return new ArrayList<>(goodsMap.values());
        }
    }

    /**
     * [구현 완료] 특정 ID의 상품 정보를 파일과 함께 조회합니다.
     * @param goodsId 조회할 상품의 ID
     * @return 파일 정보가 포함된 GoodsListDto 객체. 해당 ID의 상품이 없으면 null을 반환합니다.
     */
	@Override
	public GoodsListDto findbyPeriodWithFiles(Long goodsId) {
        // DB 스키마에 맞는 컬럼명 'goods_id'로 수정
        String sql = "SELECT " +
                     "    a.goods_id, a.goods_name, a.mobile_goods_name, a.sales_price, a.buy_price, " +
                     "    a.origin, a.insert_at, a.insert_id, a.modified_at, a.update_id, a.ai_check_yn, " +
                     "    b.files_id, b.file_path, b.file_name " +
                     "FROM public.goods a LEFT OUTER JOIN public.files b " + 
                     "ON a.goods_id = b.goods_id " +
                     "WHERE a.goods_id = ? " + // WHERE 절의 컬럼명 수정
                     "ORDER BY a.goods_id ASC, b.files_id ASC";
        
        // jdbcTemplate.query와 ResultSetExtractor를 사용하여 1:N 관계의 데이터를 조회합니다.
        // query 메소드의 파라미터로 sql, ResultSetExtractor, 그리고 sql의 '?'에 바인딩 될 값을 순서대로 전달합니다.
        List<GoodsListDto> results = jdbcTemplate.query(sql, new GoodsWithFilesResultSetExtractor(), goodsId);
        
        // 결과 리스트가 비어있지 않으면 첫 번째 항목을 반환 (ID로 조회했으므로 결과는 0 또는 1개)
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        
        // 결과가 없으면 null을 반환합니다.
        return null;
	}

}
