package com.tikitaka.api.files;

import com.tikitaka.api.files.entity.Files;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class DBFilesRepository implements FilesRepository {

    private final JdbcTemplate jdbcTemplate;

    public DBFilesRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Files 객체를 데이터베이스에 저장합니다.
     * files_id는 DB에서 자동 생성되며, 저장 후 생성된 ID를 Files 객체에 설정하여 반환합니다.
     * @param file 저장할 Files 객체
     * @return DB에 저장되고 files_id가 설정된 Files 객체
     */
    @Override
    public Files save(Files file) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "INSERT INTO public.files (file_path, file_name, goods_id, insert_id, update_id, representative_yn, file_type) VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, file.getFilePath());
            ps.setString(2, file.getFileName());
            ps.setLong(3, file.getGoodsId());
            ps.setLong(4, file.getInsertId());
            ps.setLong(5, file.getUpdateId());
            ps.setString(6, file.isRepresentativeYn() ? "1" : "0");
            ps.setString(7, file.getFileType());
            return ps;
        }, keyHolder);

        // 생성된 키(files_id)를 Files 객체에 설정합니다.
        if (keyHolder.getKeys() != null) {
        	file.setFilesId(((Number) keyHolder.getKeys().get("files_id")).longValue());
        }
        return file;
    }

    /**
     * filesId를 사용하여 단일 파일 정보를 조회합니다.
     * @param filesId 조회할 파일의 ID
     * @return 조회된 파일 정보가 존재하면 Optional<Files>, 없으면 Optional.empty()
     */
    @Override
    public Optional<Files> findById(Long filesId) {
        String sql = "SELECT files_id, file_path, file_name, goods_id, insert_id, update_id, created_at, modified_at, file_type, representative_yn FROM public.files WHERE files_id = ?";
        try {
            List<Files> result = jdbcTemplate.query(sql, filesRowMapper(), filesId);
            return result.stream().findAny();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * [추가] goodsId를 사용하여 해당 상품에 연관된 모든 파일 목록을 조회합니다.
     * @param goodsId 조회할 상품의 ID
     * @return 조회된 파일 정보 리스트
     */
    @Override
    public List<Files> findByGoodsId(Long goodsId) {
        String sql = "SELECT files_id, file_path, file_name, goods_id, insert_id, update_id, created_at, modified_at, file_type, representative_yn FROM public.files WHERE goods_id = ?";
        return jdbcTemplate.query(sql, filesRowMapper(), goodsId);
    }

    /**
     * ResultSet의 각 행을 Files 객체로 매핑하는 RowMapper를 정의합니다.
     * @return Files 객체로 매핑하는 RowMapper 인스턴스
     */
    private RowMapper<Files> filesRowMapper() {
        return (rs, rowNum) -> {
            Files file = new Files(
                rs.getString("file_path"),
                rs.getString("file_name"),
                rs.getLong("goods_id"),
                rs.getLong("insert_id"),
                rs.getLong("update_id"),
                rs.getBoolean("representative_yn"),
                rs.getString("file_type")
            );
            file.setFilesId(rs.getLong("files_id"));
            file.setInsertAt(rs.getDate("created_at"));
            file.setModifiedAt(rs.getDate("modified_at"));
            return file;
        };
    }

    public boolean deleteByGoodsId(Long goodsId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "delete from public.files where goods_id = ?";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, goodsId);
            return ps;
        }, keyHolder);

        return true;
    }

	@Override
	public boolean delete(Long filesId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String sql = "delete from public.files where files_id = ?";

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, filesId);
            return ps;
        }, keyHolder);

        return true;
	}
}
