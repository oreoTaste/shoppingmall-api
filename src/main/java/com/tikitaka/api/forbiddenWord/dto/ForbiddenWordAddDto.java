package com.tikitaka.api.forbiddenWord.dto;


import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;

import com.tikitaka.api.forbiddenWord.entity.ForbiddenWord;

import ch.qos.logback.core.util.StringUtil;
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
        forbiddenWord.setReason(StringUtil.isNullOrEmpty(this.reason) ? null : this.reason);
        forbiddenWord.setCompanyCode(StringUtil.isNullOrEmpty(this.companyCode) ? null : this.companyCode);
        forbiddenWord.setLgroup(StringUtil.isNullOrEmpty(this.getLgroup()) ? null : this.getLgroup());
        forbiddenWord.setMgroup(StringUtil.isNullOrEmpty(this.getMgroup()) ? null : this.getMgroup());
        forbiddenWord.setSgroup(StringUtil.isNullOrEmpty(this.getSgroup()) ? null : this.getSgroup());
        forbiddenWord.setDgroup(StringUtil.isNullOrEmpty(this.getDgroup()) ? null : this.getDgroup());

        return forbiddenWord;
    }
}
