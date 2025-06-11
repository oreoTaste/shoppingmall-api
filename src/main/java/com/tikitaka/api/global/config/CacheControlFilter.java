package com.tikitaka.api.global.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component; // Spring Bean으로 등록하기 위해 추가

import java.io.IOException;

/**
 * 모든 HTTP 응답에 캐싱 방지 헤더를 추가하는 필터.
 * 브라우저가 API 응답을 캐시하지 않고 항상 최신 데이터를 서버에서 가져오도록 강제합니다.
 */
@Component // 이 클래스를 Spring Bean으로 등록하여 필터 체인에 자동으로 포함되도록 합니다.
public class CacheControlFilter implements Filter {

    /**
     * 필터 초기화 메서드 (현재는 특별한 초기화 로직 없음).
     * @param filterConfig 필터 설정 객체
     * @throws ServletException 서블릿 예외 발생 시
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 필터 초기화 시 필요한 로직을 여기에 추가할 수 있습니다.
    }

    /**
     * 실제 필터 로직이 구현되는 메서드.
     * 모든 HTTP 응답에 캐싱 관련 헤더를 추가합니다.
     * @param request 서블릿 요청 객체
     * @param response 서블릿 응답 객체
     * @param chain 필터 체인 객체
     * @throws IOException 입출력 예외 발생 시
     * @throws ServletException 서블릿 예외 발생 시
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // 응답 객체를 HttpServletResponse로 캐스팅하여 헤더를 추가할 수 있도록 합니다.
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 캐싱 방지 헤더 추가
        // "no-store": 캐시를 아예 저장하지 않도록 지시합니다. (가장 강력한 캐시 방지)
        // "no-cache": 캐시를 저장할 수 있지만, 사용하기 전에 항상 서버에 유효한지 물어보도록 합니다.
        // "must-revalidate": 캐시가 오래되었을 경우 반드시 서버에 재확인하도록 합니다.
        // "proxy-revalidate": 프록시 캐시에 대한 지시어입니다.
        httpResponse.setHeader("Cache-Control", "no-cache");
        
        // HTTP 1.0 호환성을 위한 헤더 (현대에는 Cache-Control이 우선시됨)
        httpResponse.setHeader("Pragma", "no-cache");
        
        // 캐시 만료 시간을 과거로 설정하여 즉시 만료되도록 합니다.
        httpResponse.setHeader("Expires", "0"); // 또는 "-1"

        System.out.println("doFilter");
        // 다음 필터 또는 대상 서블릿/컨트롤러로 요청과 응답을 전달합니다.
        chain.doFilter(request, response);
    }

    /**
     * 필터 소멸 메서드 (현재는 특별한 소멸 로직 없음).
     */
    @Override
    public void destroy() {
        // 필터 소멸 시 필요한 로직을 여기에 추가할 수 있습니다.
    }
}
