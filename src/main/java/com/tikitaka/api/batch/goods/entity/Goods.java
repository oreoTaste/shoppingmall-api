package com.tikitaka.api.batch.goods.entity;

import com.tikitaka.api.global.entity.CommonEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // JSON 역직렬화를 위해 기본 생성자(public Goods() {})를 자동으로 추가합니다.
public class Goods extends CommonEntity {
	// 상품의 고유 ID (테이블의 goods_id와 매핑)
	private String goodsCode;
	// 상품명
	private String goodsName;
	// 모바일용 상품명
	private String mobileGoodsName;
	// 판매 가격
	private Long salePrice;
	// 구매 가격
	private Long buyPrice;
	// 원산지 등 기타정보
	private String goodsInfo; 
	// ai검수여부
	private String aiCheckYn;
	private String lgroup; // 대분류
	private String mgroup; // 중분류
	private String dgroup; // 소분류
	private String sgroup; // 세분류

	public Goods(String goodsCode, String goodsName, String mobileGoodsName, Long salePrice, Long buyPrice, String goodsInfo, Long insertId, Long updateId) {
		this.goodsCode = goodsCode;
		this.goodsName = goodsName;
		this.mobileGoodsName = mobileGoodsName;
		this.salePrice = salePrice;
		this.buyPrice = buyPrice;
		this.goodsInfo = goodsInfo;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}
	
	public Goods(String goodsName, String mobileGoodsName, Long salePrice, Long buyPrice, String goodsInfo, Long insertId, Long updateId) {
		this.goodsName = goodsName;
		this.mobileGoodsName = mobileGoodsName;
		this.salePrice = salePrice;
		this.buyPrice = buyPrice;
		this.goodsInfo = goodsInfo;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}
	
	@Override
	public String toString() {
		return String.format("상품 ID: %d, 상품명: %s(%s), 판매 가격: %d, 구매 가격: %d, 기타정보: %s",
				goodsCode, goodsName, mobileGoodsName, salePrice, buyPrice, goodsInfo);
	}
}
