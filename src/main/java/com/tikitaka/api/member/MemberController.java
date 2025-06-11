package com.tikitaka.api.member;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tikitaka.api.global.dto.ApiResponseDto;
import com.tikitaka.api.member.dto.CustomUserDetails;
import com.tikitaka.api.member.dto.MemberAuthResponseDto;
import com.tikitaka.api.member.entity.Member;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/member")
public class MemberController {

    @Autowired
    private MemberService memberService;

    @GetMapping("/auth")
    public ResponseEntity<ApiResponseDto<MemberAuthResponseDto>> getMemberInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
    	System.out.println("userDetails : " + userDetails);
    	
        // 1. @AuthenticationPrincipal 어노테이션으로 현재 로그인된 사용자의 정보를 받습니다.
        String loginId = userDetails.getUsername();

        // 2. 받은 loginId로 서비스 계층을 통해 전체 회원 정보를 조회합니다.
        Member member = memberService.getMemberByLoginId(loginId);

        MemberAuthResponseDto memberInfo = new MemberAuthResponseDto(member.getName(), member.getLoginId(), member.getAdminYn());
        
        // 정적 팩토리 메서드를 사용하여 성공 응답 생성
        ApiResponseDto<MemberAuthResponseDto> response = ApiResponseDto.success("회원 정보 조회 성공", memberInfo);
        
        
        // 4. ResponseEntity에 담아 성공(200 OK) 상태코드와 함께 응답합니다.
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponseDto<?>> signUp(@RequestBody() Member member) {
    	System.out.println("signUp : " + member);
        boolean singUpYn = memberService.save(member);

        if (!singUpYn) {
            // 실패 처리: 409 Conflict 상태코드와 함께 실패 응답 반환
            ApiResponseDto<?> response = ApiResponseDto.fail("회원가입에 실패했습니다. 이미 존재하는 아이디일 수 있습니다.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        // 성공 처리: 201 Created 상태코드와 함께 성공 응답 반환
        ApiResponseDto<?> response = ApiResponseDto.success("회원가입에 성공했습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}