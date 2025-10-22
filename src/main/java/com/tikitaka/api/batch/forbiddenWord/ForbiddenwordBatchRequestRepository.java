package com.tikitaka.api.batch.forbiddenWord;

import java.util.List;

import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordAddDto;

public interface ForbiddenwordBatchRequestRepository {
    /**
     * 여러 개의 금칙어 배치 요청을 한 번에 저장합니다.
     * @param requests 저장할 ForbiddenWordAddDto 객체 리스트
     */
    void saveAll(List<ForbiddenWordAddDto> requests);


}