package com.tikitaka.api.forbiddenWord;

import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordAddDto;
import com.tikitaka.api.forbiddenWord.dto.ForbiddenWordSearchParam;
import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;
import com.tikitaka.api.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/forbidden-words")
@RequiredArgsConstructor
public class ForbiddenWordController {

    private final ForbiddenWordService forbiddenWordService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ForbiddenWord>>> getForbiddenWords() {
        List<ForbiddenWord> forbiddenWords = forbiddenWordService.findActiveForbiddenWords();
        return ResponseEntity.ok(ApiResponseDto.success("금칙어 목록을 성공적으로 조회했습니다.", forbiddenWords));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<?>> addForbiddenWord(@RequestBody ForbiddenWordAddDto forbiddenWordDto) {
        boolean isAdded = forbiddenWordService.addForbiddenWord(forbiddenWordDto);

        if (isAdded) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success("금칙어가 성공적으로 추가되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponseDto.fail("금칙어 추가에 실패했습니다."));
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponseDto<?>> deleteForbiddenWord(ForbiddenWordSearchParam searchParam) {
        // ID가 있는지 확인하는 로직 추가
        if (searchParam.getForbiddenWordId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseDto.fail("삭제할 금칙어의 ID를 입력해주세요."));
        }
        
    	boolean isDeleted = forbiddenWordService.deleteForbiddenWord(searchParam);

        if (isDeleted) {
            return ResponseEntity.ok(ApiResponseDto.success("금칙어가 성공적으로 삭제되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponseDto.fail("해당 금칙어를 찾을 수 없습니다."));
        }
    }
}