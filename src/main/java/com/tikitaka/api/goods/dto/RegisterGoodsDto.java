package com.tikitaka.api.goods.dto;

import lombok.Data;

@Data
public class RegisterGoodsDto {
	
	// 상품명
	private String goodsName;
	// 모바일용 상품명
	private String mobileGoodsName;
	// 판매 가격
	private Long salesPrice;
	// 구매 가격
	private Long buyPrice;
	
}
