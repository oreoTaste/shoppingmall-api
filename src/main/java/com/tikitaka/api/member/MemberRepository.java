package com.tikitaka.api.member;

import org.springframework.stereotype.Repository;

import com.tikitaka.api.member.entity.Member;

@Repository()
public interface MemberRepository {
	
	boolean save(Member member);
	
	Member findOne(String loginId);
}
