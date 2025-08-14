package com.tikitaka.api.forbiddenWord.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.sql.Date;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ForbiddenWord {
    private Long forbiddenWordId;
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

    public ForbiddenWord(String word, Date startDate, Date endDate, String reason, String companyCode, String lgroup, String mgroup, String sgroup, String dgroup) {
        this.word = word;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.companyCode = companyCode;
        this.lgroup = lgroup;
        this.mgroup = mgroup;
        this.sgroup = sgroup;
        this.dgroup = dgroup;
    }
}