package com.tikitaka.api.batch.forbiddenWord.dto;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import com.tikitaka.api.batch.forbiddenWord.entity.ForbiddenWord;

import lombok.Data;

@Data
public class HarmfulwordBatchDto {
    @CsvBindByName(column = "WORD")
    private String word;

    @CsvBindByName(column = "LGROUP")
    private String lgroup;

    @CsvBindByName(column = "MGROUP")
    private String mgroup;
    @CsvBindByName(column = "SGROUP")
    private String sgroup;

    @CsvBindByName(column = "DGROUP")
    private String dgroup;
    
    @CsvBindByName(column = "INSERT_DATE")
    @CsvDate(value = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime insertDate;    
    
    public ForbiddenWord toForbiddenWord() {
    	ForbiddenWord ForbiddenWord = new ForbiddenWord();
    	ForbiddenWord.setWord(word);
    	ForbiddenWord.setLgroup(lgroup);
    	ForbiddenWord.setMgroup(mgroup);
    	ForbiddenWord.setSgroup(sgroup);
    	ForbiddenWord.setDgroup(dgroup);
    	
        if (insertDate != null) {
    	    ForbiddenWord.setUpdatedAt(insertDate.toInstant(ZoneOffset.UTC));
        }
    	
    	return ForbiddenWord;
    }

}