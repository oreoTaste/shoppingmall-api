package com.tikitaka.api.member;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

import com.tikitaka.api.member.entity.Member;

@Repository
public class MemoryMemberRepository implements MemberRepository {

    private final Map<String, Member> memory;

    // 생성자에서 외부 주입 대신 직접 생성하도록 변경
    public MemoryMemberRepository() {
        this.memory = new HashMap<>();
    }

    @Override
    public Member findOne(String loginId) {
        if (memory.containsKey(loginId)) {
            return memory.get(loginId);
        }
        return null;
    }

    @Override
    public boolean save(Member member) {
        String loginId = member.getLoginId();

        if (memory.containsKey(loginId)) {
            return false;
        }

        this.memory.put(loginId, member);
        return true;
    }
}