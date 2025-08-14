package com.tikitaka.api.inspection;

import java.util.List;

public interface ForbiddenWordRepository {

    /**
     * 현재 날짜를 기준으로 활성화된 모든 금칙어 목록을 조회합니다.
     * @return 활성화된 금칙어 문자열 리스트
     */
    List<String> findActiveForbiddenWords();

}