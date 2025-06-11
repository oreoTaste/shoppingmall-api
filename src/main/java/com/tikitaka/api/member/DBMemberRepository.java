package com.tikitaka.api.member;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.tikitaka.api.member.entity.Member;

import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;

@Repository
@Primary // MemberRepository 타입의 Bean이 여러 개일 때, DBMemberRepository를 우선적으로 사용하도록 설정
public class DBMemberRepository implements MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public DBMemberRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean save(Member member) {
        String sql = "INSERT INTO member (login_id, name, password, admin_yn, insert_id, update_id) VALUES (?, ?, ?, ?, ?, ?)";
        try {
            // jdbcTemplate.update()는 영향받은 row의 수를 반환합니다. 1이면 성공.
            int updatedRows = jdbcTemplate.update(sql, member.getLoginId(), member.getName(), member.getPassword(), member.getAdminYn(), member.getInsertId(), member.getUpdateId());
            return updatedRows == 1;
        } catch (DuplicateKeyException e) {
            // login_id가 PRIMARY KEY이므로, 중복 시 예외가 발생합니다.
            // 이 경우 false를 반환하여 저장 실패를 알립니다.
            return false;
        }
    }

    @Override
    public Member findOne(String loginId) {
        String sql = "SELECT * FROM member WHERE login_id = ?";
        try {
            // queryForObject는 결과가 정확히 1개일 때 해당 객체를 반환합니다.
            return jdbcTemplate.queryForObject(sql, memberRowMapper(), loginId);
        } catch (EmptyResultDataAccessException e) {
            // 결과가 없을 때(0개) 예외가 발생하므로, 이 경우 null을 반환합니다.
            return null;
        }
    }

    // DB 조회 결과를 Member 객체로 변환해주는 RowMapper
    private RowMapper<Member> memberRowMapper() {
        return (rs, rowNum) -> new Member(
        		rs.getLong("member_id"),
                rs.getString("name"),
                rs.getString("login_id"),
                rs.getString("password"),
                rs.getString("admin_yn"),
                rs.getLong("insert_id"),
                rs.getLong("update_id")
        );
    }
}