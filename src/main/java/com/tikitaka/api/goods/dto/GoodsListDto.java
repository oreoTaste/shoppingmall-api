package com.tikitaka.api.goods.dto;

import java.util.ArrayList;
import java.util.List;

import com.tikitaka.api.files.dto.FilesCoreDto;
import com.tikitaka.api.global.entity.CommonEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor // JSON 역직렬화를 위해 기본 생성자(public Goods() {})를 자동으로 추가합니다.
public class GoodsListDto extends CommonEntity {
	// 상품의 고유 ID (테이블의 goods_id와 매핑)
	private Long goodsId;
	// 상품명
	private String goodsName;
	// 모바일용 상품명
	private String mobileGoodsName;
	// 판매 가격
	private Long salesPrice;
	// 구매 가격
	private Long buyPrice;
	// 상품 상세 이미지 파일
	private List<FilesCoreDto> files = new ArrayList<>();
	// 원산지
	private String origin;
	// ai검수여부
	private String aiCheckYn;
	
	private String lgroup; // 대분류
	private String lgroupName; // 대분류명
	private String mgroup; // 중분류
	private String mgroupName; // 중분류명
	private String dgroup; // 소분류
	private String dgroupName; // 소분류명
	private String sgroup; // 세분류
	private String sgroupName; // 세분류명

	
	// 모든 필드를 포함하는 생성자
	public GoodsListDto(Long goodsId, String goodsName, String mobileGoodsName, Long salesPrice, Long buyPrice, String origin, Long insertId, Long updateId) {
		this.goodsId = goodsId;
		this.goodsName = goodsName;
		this.mobileGoodsName = mobileGoodsName;
		this.salesPrice = salesPrice;
		this.buyPrice = buyPrice;
		this.origin = origin;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}
	
	public GoodsListDto(String goodsName, String mobileGoodsName, Long salesPrice, Long buyPrice, Long insertId, Long updateId) {
		this.goodsName = goodsName;
		this.mobileGoodsName = mobileGoodsName;
		this.salesPrice = salesPrice;
		this.buyPrice = buyPrice;
		this.insertId = insertId; // CommonEntity 필드
		this.updateId = updateId; // CommonEntity 필드
	}
	
	@Override
	public String toString() {
		return String.format("상품 ID: %d, 상품명: %s(%s), 판매 가격: %d, 구매 가격: %d, 파일 개수: %ㅇ",
				goodsId, goodsName, mobileGoodsName, salesPrice, buyPrice, files.size());
	}
}