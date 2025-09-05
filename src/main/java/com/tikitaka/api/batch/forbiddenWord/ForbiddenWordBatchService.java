package com.tikitaka.api.batch.forbiddenWord;

import java.util.List;

import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordAddDto;
import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;

public interface ForbiddenWordBatchService {
    /**
     * 현재 날짜를 기준으로 활성화된 모든 금칙어 목록을 조회합니다.
     * @return 활성화된 금칙어 문자열 리스트
     */
    List<ForbiddenWord> findActiveForbiddenWords(ForbiddenWordSearchParam searchParam);
    
    /**
     * 현재 날짜를 기준으로 활성화된 모든 금칙어 목록을 조회합니다.
     * @return 활성화된 금칙어 문자열 리스트
     */
    List<ForbiddenWord> findActiveForbiddenWords();

    boolean addForbiddenWord(ForbiddenWordAddDto forbiddenWord);
    
    boolean deleteForbiddenWord(ForbiddenWordSearchParam searchParam);
}
