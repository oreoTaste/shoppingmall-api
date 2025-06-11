package com.tikitaka.api.member;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class MemberServiceTest {

    private MemberService memberService;
    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;

    // @BeforeEach: 각 @Test가 실행되기 전에 이 메서드를 먼저 실행합니다.
    @BeforeEach
    void setUp() {
        // 실제 객체들을 생성하여 주입합니다.
        memberRepository = new MemoryMemberRepository();
        passwordEncoder = new BCryptPasswordEncoder(); // 실제 암호화 객체 생성
        memberService = new MemberServiceImpl(memberRepository, passwordEncoder); // 새로운 생성자에 맞게 수정
    }

    @Test
    @DisplayName("정상 회원가입 시 비밀번호가 암호화되어 저장된다")
    public void save() {
        // given
        String rawPassword = "password123";
        Member member = new Member("정상회원1", "good_member", rawPassword);

        // when
        boolean saveYn = memberService.save(member);
        Member findMember = memberRepository.findOne("good_member");

        // then
        Assertions.assertThat(saveYn).isTrue();
        Assertions.assertThat(findMember).isNotNull();
        // 1. 저장된 비밀번호가 원본과 다른지 (암호화되었는지) 확인
        Assertions.assertThat(findMember.getPassword()).isNotEqualTo(rawPassword);
        // 2. 암호화된 비밀번호가 원본과 일치하는지 matches()로 확인
        Assertions.assertThat(passwordEncoder.matches(rawPassword, findMember.getPassword())).isTrue();
    }

    @Test
    @DisplayName("중복된 아이디로 회원가입 시 실패한다")
    public void saveWithDuplicateLoginId() {
        // given
        Member member1 = new Member("회원1", "duplicate_id", "pass1");
        memberService.save(member1);

        // when
        Member member2 = new Member("회원2", "duplicate_id", "pass2");
        boolean saveYn = memberService.save(member2);

        // then
        Assertions.assertThat(saveYn).isFalse();
    }

    @Test
    @DisplayName("loginId로 UserDetails 조회 시 암호화된 정보를 반환한다")
    public void loadUserByUsername() {
        // given
        String loginId = "user1";
        String rawPassword = "password123";
        Member member = new Member("사용자1", loginId, rawPassword);
        memberService.save(member);

        // when
        UserDetails userDetails = memberService.loadUserByUsername(loginId);

        // then
        Assertions.assertThat(userDetails.getUsername()).isEqualTo(loginId);
        // UserDetails에 담긴 비밀번호가 암호화된 비밀번호와 일치하는지 확인
        Assertions.assertThat(userDetails.getPassword()).isEqualTo(memberRepository.findOne(loginId).getPassword());
    }
    
    // 이름, 아이디, 비밀번호가 비거나 null인 경우의 테스트는 기존과 거의 동일합니다.
    // 예시: 이름이 비어있는 경우
    @Test
    @DisplayName("비어있는 이름으로 회원가입 시 실패한다")
    public void saveWithEmptyName() {
        // given
        Member member = new Member("", "bad_member1", "password");

        // when
        boolean saveYn = memberService.save(member);

        // then
        Assertions.assertThat(saveYn).isFalse();
    }

    // ... 기타 비정상 케이스 테스트들 ...
}