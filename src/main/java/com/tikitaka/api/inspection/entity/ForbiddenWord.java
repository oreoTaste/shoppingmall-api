package com.tikitaka.api.inspection.entity;

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

    public ForbiddenWord(String word, Date startDate, Date endDate, String reason, String companyCode) {
        this.word = word;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.companyCode = companyCode;
    }
}