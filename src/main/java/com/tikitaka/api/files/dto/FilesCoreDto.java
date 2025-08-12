package com.tikitaka.api.files.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString(callSuper = true) // 부모 클래스의 필드도 toString에 포함시킵니다.
public class FilesCoreDto {

    // [수정] 이 엔티티의 고유한 기본 키(Primary Key)를 명확하게 추가합니다.
    private Long filesId;

    private String fileType; // 파일 타입(태그로 저장 혹은 파일로 저장)
    private String filePath; // 웹에서 접근 가능한 경로 (예: /uploads/image.jpg)
    private String fileName; // 원본 파일 이름
    private boolean representativeYn; // 대표사진여부
    private Long goodsId;    // 이 파일이 속한 상품의 ID

    public FilesCoreDto(String filePath, String fileName, Long goodsId, boolean representativeYn) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.goodsId = goodsId;
        this.representativeYn = representativeYn;
    }
}
