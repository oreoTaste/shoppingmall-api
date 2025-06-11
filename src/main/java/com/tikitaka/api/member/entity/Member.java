package com.tikitaka.api.member.entity;

import com.tikitaka.api.global.entity.CommonEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // JSON 역직렬화를 위해 기본 생성자(public Goods() {})를 자동으로 추가합니다.
public class Member extends CommonEntity{

	// 회원의 고유 ID (테이블의 member_id와 매핑)
	private Long memberId;
	// 회원 이름
	private String name;
	// 로그인 ID
	private String loginId;
	// 비밀번호
	private String password;
	// 관리자 여부 ('Y' 또는 'N')
	private String adminYn;
	
	
	// 모든 필드를 포함하는 생성자
	public Member(Long memberId, String name, String loginId, String password, String adminYn, Long insertId, Long updateId) {
		this.memberId = memberId;
		this.loginId = loginId;
		this.name = name;
		this.password = password;
		this.adminYn = adminYn;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}	

	public Member(String name, String loginId, String password, String adminYn, Long insertId, Long updateId) {
		this.loginId = loginId;
		this.name = name;
		this.password = password;
		this.adminYn = adminYn;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}
	
	@Override
	public String toString() {
		return String.format("member = {memberId: %d, name: %s, loginId: %s, password: %s, adminYn: %s}",
				memberId, name, loginId, password, adminYn);
	}
}
