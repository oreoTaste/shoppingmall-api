package com.tikitaka.api.goods.dto;

import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.member.dto.CustomUserDetails;
import ch.qos.logback.core.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoodsInspectRequestDto {

    private String goodsId;
    private String goodsName;
    private String mobileGoodsName;
    private String salesPrice;
    private String buyPrice;
    private String origin;
    private String imageType;
    private String imageHtml;
    private MultipartFile[] representativeFile;
    private MultipartFile[] imageFiles;
    private String lgroup;
    private String mgroup;
    private String sgroup;
    private String dgroup;
    

    /**
     * 요청 DTO를 Goods 엔티티로 변환합니다.
     * @param userDetails 현재 로그인한 사용자 정보
     * @return Goods 엔티티
     */
    public Goods toEntity(CustomUserDetails userDetails) {
        Goods goods = new Goods(
                this.goodsName,
                this.mobileGoodsName,
                Long.valueOf(this.salesPrice),
                Long.valueOf(this.buyPrice),
                StringUtil.isNullOrEmpty(this.origin) ? "국내산" : this.origin,
                userDetails.getMemberId(),
                userDetails.getMemberId()
        );

        if (!StringUtil.isNullOrEmpty(this.goodsId)) {
            goods.setGoodsId(Long.valueOf(this.goodsId));
        }
        return goods;
    }
}