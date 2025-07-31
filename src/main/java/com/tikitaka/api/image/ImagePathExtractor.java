package com.tikitaka.api.image;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public class ImagePathExtractor {

    public static List<String> extractImageUrls(String html) {
        List<String> imageUrls = new ArrayList<>();
        
        // src 속성의 값을 찾는 정규 표현식
        Pattern pattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            imageUrls.add(matcher.group(1));
        }
        return imageUrls;
    }
}