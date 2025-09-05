package com.tikitaka.api.batch.goods.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class GoodsBatchDto {
    @CsvBindByName(column = "goodsName")
    private String goodsName;

    @CsvBindByName(column = "mobileGoodsName")
    private String mobileGoodsName;

    @CsvBindByName(column = "salesPrice")
    private String salesPrice;

    @CsvBindByName(column = "buyPrice")
    private String buyPrice;

    @CsvBindByName(column = "origin")
    private String origin;

    @CsvBindByName(column = "imageHtml")
    private String imageHtml;

    @CsvBindByName(column = "representativeFile")
    private String representativeFile; // 대표 이미지 파일명

    @CsvBindByName(column = "imageFiles")
    private String imageFiles;         // 추가 이미지 파일명 (쉼표로 구분)

    @CsvBindByName(column = "lgroup")
    private String lgroup;

    @CsvBindByName(column = "lgroupName")
    private String lgroupName;

    @CsvBindByName(column = "mgroup")
    private String mgroup;

    @CsvBindByName(column = "mgroupName")
    private String mgroupName;

    @CsvBindByName(column = "sgroup")
    private String sgroup;

    @CsvBindByName(column = "sgroupName")
    private String sgroupName;

    @CsvBindByName(column = "dgroup")
    private String dgroup;

    @CsvBindByName(column = "dgroupName")
    private String dgroupName;

}