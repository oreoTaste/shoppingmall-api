package com.tikitaka.api.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class Member {

	private String name;
	private String loginId;
	private String password;
	private String adminYn;
	
	public Member(String name, String loginId, String password, String adminYn) {
		this.loginId = loginId;
		this.name = name;
		this.password = password;
		this.adminYn = adminYn;
	}
	

	@Override
	public String toString() {
		return String.format("member = {name: %s, loginId: %s, password: %s}", name, loginId, password);
	}


}
