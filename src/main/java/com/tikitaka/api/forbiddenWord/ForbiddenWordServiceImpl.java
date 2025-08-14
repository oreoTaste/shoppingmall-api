package com.tikitaka.api.forbiddenWord;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordAddDto;
import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ForbiddenWordServiceImpl implements ForbiddenWordService{

	private final ForbiddenWordRepository forbiddenWordRepository;
	
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
