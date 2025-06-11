package com.tikitaka.api.member.dto;

import lombok.Getter;

@Getter
public class MemberAuthResponseDto {
    private String name;
    private String loginId;
    private String adminYn;

    public MemberAuthResponseDto(String name, String loginId, String adminYn) {
        this.name = name;
        this.loginId = loginId;
        this.adminYn = adminYn;
    }

}