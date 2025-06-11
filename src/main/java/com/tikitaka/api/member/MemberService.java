package com.tikitaka.api.member;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service()
public interface MemberService extends UserDetailsService {
	
	Member getMemberByLoginId(String loginId); // 이 메서드 선언을 추가합니다.
	
	boolean save(Member member);
}
