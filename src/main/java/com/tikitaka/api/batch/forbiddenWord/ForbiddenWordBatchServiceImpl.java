package com.tikitaka.api.batch.forbiddenWord;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordAddDto;
import com.tikitaka.api.batch.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ForbiddenWordBatchServiceImpl implements ForbiddenWordBatchService{

	private final ForbiddenWordBatchRepository forbiddenWordRepository;
	
	@Override
    @Transactional(readOnly = true)
	public List<ForbiddenWord> findActiveForbiddenWords(ForbiddenWordSearchParam searchParam) {
		return this.forbiddenWordRepository.findActiveForbiddenWords(searchParam);
	}
	
    @Override
    @Transactional(readOnly = true)
    public List<ForbiddenWord> findActiveForbiddenWords() {
        return forbiddenWordRepository.findActiveForbiddenWords();
    }

    @Override
    public boolean addForbiddenWord(ForbiddenWordAddDto addDto) {
        return forbiddenWordRepository.save(addDto.toEntity());
    }

    @Override
    public boolean deleteForbiddenWord(ForbiddenWordSearchParam searchParam) {
        return forbiddenWordRepository.deactivateById(searchParam.getForbiddenWordId());
    }

}
