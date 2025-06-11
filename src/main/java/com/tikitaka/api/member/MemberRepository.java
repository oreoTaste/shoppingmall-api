package com.tikitaka.api.member;

import org.springframework.stereotype.Repository;

@Repository()
public interface MemberRepository {
	
	boolean save(Member member);
	
	Member findOne(String loginId);
}
