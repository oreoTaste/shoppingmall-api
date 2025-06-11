package com.tikitaka.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // application.properties에 설정된 파일 업로드 디렉토리 경로
    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // [핵심] '/uploads/**' URL 패턴으로 요청이 들어오면,
        // 실제 'file:./uploads/' 디렉토리에서 파일을 찾아 제공하도록 매핑합니다.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
