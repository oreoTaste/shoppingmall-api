package com.tikitaka.api.member;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class MemberRepositoryTest {

    private MemberRepository memberRepository;

    @BeforeEach
    void setup() {
        // 각 테스트 실행 전에 깨끗한 Repository 인스턴스를 생성
        this.memberRepository = new MemoryMemberRepository();
    }

    @Test
    @DisplayName("회원 정보를 성공적으로 저장한다")
    void save_success() {
        // given
        Member member = new Member("정상회원1", "good_member", "password");

        // when
        boolean result = memberRepository.save(member);

        // then
        Assertions.assertThat(result).isTrue();
    }

    @Test
    @DisplayName("저장된 회원을 아이디로 성공적으로 조회한다")
    void findOne_success() {
        // given
        Member member = new Member("정상회원1", "good_member", "password");
        memberRepository.save(member);

        // when
        Member findMember = memberRepository.findOne("good_member");

        // then
        Assertions.assertThat(findMember).isNotNull();
        Assertions.assertThat(findMember.getLoginId()).isEqualTo(member.getLoginId());
        Assertions.assertThat(findMember.getName()).isEqualTo(member.getName());
    }
    
    @Test
    @DisplayName("중복된 아이디로 저장을 시도하면 실패한다")
    void save_fail_duplicateLoginId() {
        // given
        Member member1 = new Member("회원1", "duplicate_id", "pass1");
        memberRepository.save(member1); // 먼저 한 명을 저장

        Member member2 = new Member("회원2", "duplicate_id", "pass2"); // 동일한 아이디로 다른 회원 생성

        // when
        boolean result = memberRepository.save(member2); // 중복 저장 시도

        // then
        Assertions.assertThat(result).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 조회하면 null을 반환한다")
    void findOne_fail_nonExistentLoginId() {
        // given
        // 아무것도 저장하지 않은 상태

        // when
        Member findMember = memberRepository.findOne("non_existent_id");

        // then
        Assertions.assertThat(findMember).isNull();
    }
}