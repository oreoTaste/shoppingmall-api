// MemberServiceImpl.java

package com.tikitaka.api.member;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder; // 추가
import org.springframework.stereotype.Service;

import com.tikitaka.api.member.dto.CustomUserDetails;
import com.tikitaka.api.member.entity.Member;

@Service
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder; // PasswordEncoder 주입

    // 생성자를 통해 PasswordEncoder를 주입받습니다.
    public MemberServiceImpl(MemberRepository memberRepository, PasswordEncoder passwordEncoder) { //
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean save(Member member) {
        String name = member.getName();
        String loginId = member.getLoginId();
        String rawPassword = member.getPassword();

        if (name == null || name.isBlank() || loginId == null || loginId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return false;
        }
        
        Long memberId = member.getMemberId();
        member.setAdminYn("Y");
        member.setInsertId(memberId);
        member.setUpdateId(memberId);

        // 비밀번호를 암호화하여 다시 Member 객체에 설정
        String encodedPassword = passwordEncoder.encode(rawPassword);
        member.setPassword(encodedPassword);

        return memberRepository.save(member);
    }

    @Override
    public Member getMemberByLoginId(String loginId) {
        return memberRepository.findOne(loginId);
    }   
    
    // Spring Security가 로그인 시 호출하는 핵심 메서드
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        // loginId로 실제 저장소에서 회원 정보를 가져옵니다.
        Member member = memberRepository.findOne(loginId);
        
        if (member == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + loginId);
        }
        
        // UserDetails 객체를 생성하여 반환합니다.
        // Spring Security가 이 정보를 바탕으로 비밀번호를 비교하고 인증을 처리합니다.
        return new CustomUserDetails(
        		member.getMemberId(),
                member.getLoginId(),
                member.getName(),
                member.getPassword(),
                member.getAdminYn() // DB의 adminYn 값을 포함시킵니다.
            );
    }
}