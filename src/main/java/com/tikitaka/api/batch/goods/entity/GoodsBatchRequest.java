package com.tikitaka.api.batch.goods.entity;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
public class GoodsBatchRequest {
    private Long requestId;
    private String batchJobId;
    private String status;
    private String goodsCode;
    private String goodsName;
    private String mobileGoodsName;
    private BigDecimal salePrice;
    private BigDecimal buyPrice;
    private String goodsInfo;
    private String imageHtml;
    private String representativeFile;
    private String lgroup;
    private String lgroupName;
    private String mgroup;
    private String mgroupName;
    private String sgroup;
    private String sgroupName;
    private String dgroup;
    private String dgroupName;
    private String inspectionStatus;
    private String errorMessage;
    private int retries;
    
    /**
     * GoodsBatchRequest 엔티티를 AI 검수에 필요한 Goods 엔티티로 변환합니다.
     * @return Goods 엔티티
     */
    public Goods toGoodsEntity() {
        Goods goods = new Goods();
        goods.setGoodsCode(this.goodsCode);
        goods.setGoodsName(this.goodsName);
        goods.setMobileGoodsName(this.mobileGoodsName);
        goods.setSalePrice(this.salePrice != null ? this.salePrice.longValue() : 0L);
        goods.setBuyPrice(this.buyPrice != null ? this.buyPrice.longValue() : 0L);
        goods.setGoodsInfo(this.goodsInfo);
        goods.setLgroup(this.lgroup);
        goods.setMgroup(this.mgroup);
        goods.setSgroup(this.sgroup);
        goods.setDgroup(this.dgroup);
        return goods;
    }
}