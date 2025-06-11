package com.tikitaka.api.member.dto;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

//UserDetails를 구현하여 Spring Security가 사용자인증 정보를 담을 수 있도록 합니다.
@Getter
public class CustomUserDetails implements UserDetails {
	private final Long memberId;
	private final String loginId;
	private final String name;
	private final String password;
	private final String adminYn; // DB에서 가져온 추가 정보
	private final Collection<? extends GrantedAuthority> authorities;

	// 생성자에서 Member 엔티티 등 DB에서 조회한 객체를 받아와 필드를 초기화합니다.
	public CustomUserDetails(Long memberId, String loginId, String name, String password, String adminYn) {
		this.memberId = memberId;
		this.loginId = loginId;
		this.name = name;
		this.password = password;
		this.adminYn = adminYn;
		// 필요에 따라 권한 설정
		this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getUsername() {
		// Spring Security에서는 username을 식별자로 사용합니다. 우리는 loginId를 사용합니다.
		return loginId;
	}

	public String getAdminYn() {
		return adminYn;
	}

	// 계정 만료, 잠금 등 세부 설정은 필요에 따라 true로 설정합니다.
	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String toString() {
		return "CustomUserDetails = {memberId:" + memberId + ", loginId:" + loginId + ", name: " + name + ", password:" + password
				+ ", adminYn:" + adminYn + ", authorities:" + authorities + "}";
	}
	

}