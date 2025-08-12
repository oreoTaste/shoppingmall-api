package com.tikitaka.api.files;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.files.entity.Files;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.member.dto.CustomUserDetails;

public interface FilesService {
	
    /**
     * [추가] goodsId를 기준으로 파일 목록을 조회하는 서비스 메소드
     */
    List<Files> findByGoodsId(Long goodsId);
    
    /**
     * [추가] DB에서 조회한 파일 정보 리스트를 기반으로,
     * 실제 서버 디스크에서 파일들을 읽어와 FileContent 리스트로 반환합니다.
     * @param files DB에서 조회한 Files 엔티티 리스트
     * @return 파일의 실제 내용이 담긴 FileContent 리스트
     * @throws IOException 파일 읽기 중 오류 발생 시
     */
    List<FileContent> readFiles(List<Files> files) throws IOException;

    /**
     * filesId에 해당하는 파일데이터를 삭제합니다.
     */
	boolean deleteByFilesId(Long filesId);

    /**
     * goodsId에 해당하는 파일데이터를 삭제합니다.
     */
    boolean deleteDBFilesByGoodsId(Long goodsId);
    
    /**
     * goodsId에 해당하는 물리적 파일들을 삭제합니다.
     */
    void deleteFilesByGoodsId(Long goodsId);

    /**
     * filesId에 해당하는 물리적 파일들을 삭제합니다.
     */
    void deleteFilesByFilesId(Long filesId);
    /**
     * 파일 목록 저장
     */
	void save(Goods goods, MultipartFile[] files, CustomUserDetails userDetails, boolean representativeYn) throws IOException;

    /**
     * 파일 목록 저장
     */
	void save(Goods goods, MultipartFile[] files, CustomUserDetails userDetails) throws IOException;

    /**
     * 파일 목록 저장
     */
	void save(Goods goods, String fileTag, CustomUserDetails userDetails) throws IOException;

	
}
