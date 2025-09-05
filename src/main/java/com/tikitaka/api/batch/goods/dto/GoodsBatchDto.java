package com.tikitaka.api.batch.goods.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

@Data
public class GoodsBatchDto {
    @CsvBindByName(column = "GOODS_CODE")
    private String goodsCode;

    @CsvBindByName(column = "GOODS_NAME")
    private String goodsName;

    @CsvBindByName(column = "MOBILE_GOODS_NAME")
    private String mobileGoodsName;

    @CsvBindByName(column = "SALE_PRICE")
    private String salePrice;

    @CsvBindByName(column = "BUY_PRICE")
    private String buyPrice;

    @CsvBindByName(column = "GOODS_INFO")
    private String goodsInfo;

    @CsvBindByName(column = "IMAGE_HTML")
    private String imageHtml;

    @CsvBindByName(column = "REPRESENTATIVE_FILE")
    private String representativeFile; // 대표 이미지 파일명

    @CsvBindByName(column = "LGROUP")
    private String lgroup;

    @CsvBindByName(column = "LGROUP_NAME")
    private String lgroupName;

    @CsvBindByName(column = "MGROUP")
    private String mgroup;

    @CsvBindByName(column = "MGROUP_NAME")
    private String mgroupName;

    @CsvBindByName(column = "SGROUP")
    private String sgroup;

    @CsvBindByName(column = "SGROUP_NAME")
    private String sgroupName;

    @CsvBindByName(column = "DGROUP")
    private String dgroup;

    @CsvBindByName(column = "DGROUP_NAME")
    private String dgroupName;

}