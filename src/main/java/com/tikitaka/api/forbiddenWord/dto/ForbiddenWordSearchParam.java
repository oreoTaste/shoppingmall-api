package com.tikitaka.api.forbiddenWord.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForbiddenWordSearchParam {
    private Long forbiddenWordId;
	private String lgroup;	// 대분류 (VARCHAR(2))
	private String mgroup;	// 중분류 (VARCHAR(2))
	private String sgroup;	// 소분류 (VARCHAR(2))
	private String dgroup;	// 세분류 (VARCHAR(2))
	private String companyCode;	// TENTERPRISE.ENTP_CODE (VARCHAR(6))
	private String word;	// 검색어
}
