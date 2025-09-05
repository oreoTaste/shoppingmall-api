package com.tikitaka.api.batch.forbiddenWord;

import java.util.List;

import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;

public interface ForbiddenWordBatchRepository {

    /**
     * 현재 날짜를 기준으로 활성화된 모든 금칙어 목록을 조회합니다.
     * @return 활성화된 금칙어 문자열 리스트
     */
    List<ForbiddenWord> findActiveForbiddenWords();
    
    /**
     * 현재 날짜를 기준으로 활성화된 모든 금칙어 목록을 조회합니다.
     * @return 활성화된 금칙어 문자열 리스트
     */
    List<ForbiddenWord> findActiveForbiddenWords(ForbiddenWordSearchParam searchParam);
    
    /**
     * 새로운 금칙어를 저장합니다.
     * @param forbiddenWord 저장할 금칙어 객체
     * @return 저장 성공 여부
     */
    boolean save(ForbiddenWord forbiddenWord);

    /**
     * 특정 단어를 금칙어 목록에서 삭제합니다.
     * @param word 삭제할 금칙어
     * @return 삭제 성공 여부
     */
    boolean deactivateById(Long id);

}