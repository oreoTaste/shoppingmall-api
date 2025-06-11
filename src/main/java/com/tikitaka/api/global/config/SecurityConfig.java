package com.tikitaka.api.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tikitaka.api.member.dto.CustomUserDetails;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Configuration
public class SecurityConfig {

    // ObjectMapper를 Bean으로 등록하여 재사용성을 높이고 일관성을 유지합니다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/member/sign-up", "/member/login", "/goods/list").permitAll() // 회원가입과 로그인은 누구나 접근 가능
                .anyRequest().authenticated() // 나머지 모든 요청은 인증 필요
        );

        http.formLogin(form -> form
                .loginProcessingUrl("/member/login") // 로그인 요청을 처리할 URL
                // 👇 [수정] 프론트엔드에서 보내는 파라미터 이름과 동일하게 'loginId'로 변경합니다.
                .usernameParameter("loginId") 
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                	System.out.println("로그인 성공 - loginId : " + request.getParameter("loginId") + " , password : " + request.getParameter("password"));
                    response.setStatus(HttpStatus.OK.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    // 1. Authentication 객체에서 Principal(사용자 정보)을 가져옵니다.
                    Object principal = authentication.getPrincipal();

                    // 2. Principal이 CustomUserDetails 타입인지 확인하고 캐스팅합니다.
                    if (principal instanceof CustomUserDetails) {
                        CustomUserDetails userDetails = (CustomUserDetails) principal;

                        // 3. CustomUserDetails에서 DB에서 가져온 adminYn 값을 꺼냅니다.
                        String adminYn = userDetails.getAdminYn();

                        // 응답 설정
                        response.setStatus(HttpStatus.OK.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");

                        Map<String, Object> successResponse = new HashMap<>();
                        successResponse.put("success", true);
                        successResponse.put("message", "로그인 성공");
                        // 4. 조회한 값을 응답에 포함시킵니다.
                        successResponse.put("adminYn", adminYn);

                        response.getWriter().write(objectMapper.writeValueAsString(successResponse));
                    }
                })
                .failureHandler((request, response, exception) -> {
                	Enumeration<String> parameterNames = request.getParameterNames();
                	while(parameterNames.hasMoreElements()) {
                    	System.out.println("parameterNames" + parameterNames.nextElement());
                	}
                	System.out.println("로그인 실패 - loginId : " + request.getParameter("loginId") + " , password : " + request.getParameter("password"));
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");

                    Map<String, Object> failResponse = new HashMap<>();
                    failResponse.put("success", false);
                    failResponse.put("message", "로그인 실패: " + exception.getMessage());
                    
                    response.getWriter().write(objectMapper.writeValueAsString(failResponse));
                })
        );
        
        return http.build();
    }
}