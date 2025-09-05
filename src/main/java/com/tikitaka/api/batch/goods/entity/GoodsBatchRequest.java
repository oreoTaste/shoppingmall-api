package com.tikitaka.api.batch.goods.entity;

import java.math.BigDecimal;

import com.tikitaka.api.goods.entity.Goods;

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
    private String goodsName;
    private String mobileGoodsName;
    private BigDecimal salesPrice;
    private BigDecimal buyPrice;
    private String origin;
    private String imageHtml;
    private String representativeFilePath;
    private String imageFilesPaths;
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
        goods.setGoodsName(this.goodsName);
        goods.setMobileGoodsName(this.mobileGoodsName);
        goods.setSalesPrice(this.salesPrice != null ? this.salesPrice.longValue() : 0L);
        goods.setBuyPrice(this.buyPrice != null ? this.buyPrice.longValue() : 0L);
        goods.setOrigin(this.origin);
        goods.setLgroup(this.lgroup);
        goods.setMgroup(this.mgroup);
        goods.setSgroup(this.sgroup);
        goods.setDgroup(this.dgroup);
        // 검수 시점에는 goodsId가 없으므로 null로 둡니다.
        return goods;
    }
}