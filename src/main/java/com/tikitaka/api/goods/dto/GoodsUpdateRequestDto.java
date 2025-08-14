package com.tikitaka.api.goods.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class GoodsUpdateRequestDto {
    private Long goodsId;
    private String goodsName;
    private String mobileGoodsName;
    private String salesPrice;
    private String buyPrice;
    private String origin;
    private String imageType;
    private MultipartFile[] representativeFile;
    private MultipartFile[] files;
    private String imageHtml;
}