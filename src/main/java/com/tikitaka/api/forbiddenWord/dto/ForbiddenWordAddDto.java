package com.tikitaka.api.forbiddenWord.dto;


import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;

import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForbiddenWordAddDto {
    private String word;
    private String companyCode;
    private Date startDate;
    private Date endDate;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;
	private String lgroup;	// 대분류 (VARCHAR(2))
	private String mgroup;	// 중분류 (VARCHAR(2))
	private String sgroup;	// 소분류 (VARCHAR(2))
	private String dgroup;	// 세분류 (VARCHAR(2))

    /**
     * DTO를 ForbiddenWord 엔티티로 변환합니다.
     * @return ForbiddenWord 엔티티
     */
    public ForbiddenWord toEntity() {
        // DTO의 필드를 사용하여 ForbiddenWord 객체 생성
        ForbiddenWord forbiddenWord = new ForbiddenWord();
        forbiddenWord.setWord(this.word);
        
        // 클라이언트에서 값을 보내지 않은 경우, 기본값을 설정합니다.
        forbiddenWord.setStartDate(this.startDate != null ? this.startDate : Date.valueOf(LocalDate.now()));
        forbiddenWord.setEndDate(this.endDate != null ? this.endDate : Date.valueOf("9999-12-31"));
        forbiddenWord.setReason(this.reason);
        forbiddenWord.setCompanyCode(this.companyCode);
        forbiddenWord.setLgroup(this.getLgroup());
        forbiddenWord.setMgroup(this.getMgroup());
        forbiddenWord.setSgroup(this.getSgroup());
        forbiddenWord.setDgroup(this.getDgroup());

        return forbiddenWord;
    }
}
