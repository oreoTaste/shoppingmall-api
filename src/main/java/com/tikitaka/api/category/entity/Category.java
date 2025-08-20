package com.tikitaka.api.category.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Category {
    private Long categoryId;
    private int level;
    private String code;
    private String name;
    private String parentCode;
}