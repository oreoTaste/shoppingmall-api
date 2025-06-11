package com.tikitaka.api.goods;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.files.FilesService;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.member.dto.CustomUserDetails;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

/**
 * GoodsService 인터페이스의 구현체.
 * 실제 상품 관련 비즈니스 로직을 처리하며, GoodsRepository를 통해 데이터베이스와 상호작용합니다.
 */
@Service // 이 클래스를 Spring의 서비스 빈으로 등록합니다.
@Transactional // 클래스 레벨에서 트랜잭션을 적용하여 모든 공용 메서드에 트랜잭션이 적용됩니다.
@Slf4j
public class GoodsServiceImpl implements GoodsService {

    private final FilesService filesService;

    private final GoodsRepository goodsRepository; // GoodsRepository 주입을 위한 필드

    /**
     * GoodsRepository를 주입받는 생성자.
     * Spring이 이 서비스를 생성할 때 자동으로 GoodsRepository 빈을 찾아 주입합니다.
     * @param goodsRepository 상품 데이터 접근을 위한 Repository
     */
    public GoodsServiceImpl(GoodsRepository goodsRepository, FilesService filesService) {
        this.goodsRepository = goodsRepository;
        this.filesService = filesService;
    }

    /**
     * 새로운 상품을 저장합니다.
     * @param goods 저장할 Goods 객체
     * @return 저장된 Goods 객체 (goodsId가 포함될 수 있음)
     */
    @Override
    public Goods save(Goods goods) {
        // 비즈니스 로직 추가 가능: 예) 상품명 유효성 검사, 기본값 설정 등
        return goodsRepository.save(goods);
    }


    /**
     * 주어진 기간 동안의 모든 상품 목록을 반환합니다.
     * 현재는 repository의 findAllbyPeriod()를 호출하지만, 서비스 계층에서 기간 로직을 추가할 수 있습니다.
     * @return 기간에 해당하는 상품의 리스트
     */
    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
    public List<GoodsListDto> findAllbyPeriodWithFiles() {
        return goodsRepository.findAllbyPeriodWithFiles();
    }

    /**
     * 주어진 기간 동안의 특정 상품을 반환합니다.
     * 현재는 findOne()과 동일하게 구현되지만, 필요에 따라 기간 필터링 로직을 추가할 수 있습니다.
     * @return 기간에 해당하는 상품의 리스트
     */
    @Override
    @Transactional(readOnly = true) // 읽기 전용 트랜잭션
	public GoodsListDto findbyPeriodWithFiles(Long goodsId) {
        return goodsRepository.findbyPeriodWithFiles(goodsId);
    }

    
    /**
     * 주어진 goodsId의 상품을 삭제합니다.
     * @param goodsId 삭제할 상품의 ID
     * @return 삭제 성공 여부
     */
    @Override
    public boolean delete(Long goodsId) {
    	try {
        	filesService.deleteFilesByGoodsId(goodsId);
    	} catch(Exception e) {
            log.error("파일을 삭제하는 중 오류 발생", e);
    	}
    	
        // 비즈니스 로직 추가 가능: 예) 관련 데이터(예: 주문 내역) 확인 후 삭제 여부 결정 등
        return goodsRepository.delete(goodsId);
    }

    /**
     * [구현] 상품 정보와 파일을 함께 업데이트하는 트랜잭션 메소드
     */
    @Override
    @Transactional
    public boolean updateWithFiles(Goods goods, MultipartFile[] imageFiles, CustomUserDetails userDetails) throws IOException {
        // 1. 상품 정보(텍스트)를 먼저 업데이트합니다.
        boolean isGoodsUpdated = goodsRepository.update(goods);
        if (!isGoodsUpdated) {
            log.warn("업데이트할 상품을 찾지 못했습니다. ID: {}", goods.getGoodsId());
            return false;
        }

        // 2. 새로운 파일이 첨부되었는지 확인합니다.
        boolean hasNewFiles = imageFiles != null && imageFiles.length > 0 && !imageFiles[0].isEmpty();
        
        if (hasNewFiles) {
            log.info("새로운 파일이 감지되어 기존 파일을 삭제하고 새 파일을 저장합니다. Goods ID: {}", goods.getGoodsId());
            
            // 3. 만약 새 파일이 있다면, 기존의 모든 물리적 파일과 DB 레코드를 삭제합니다.
            filesService.deleteFilesByGoodsId(goods.getGoodsId()); // 물리적 파일 삭제
            filesService.delete(goods.getGoodsId());  // DB 레코드 삭제
            
            // 4. 새로운 파일들을 저장합니다.
            filesService.save(goods, imageFiles, userDetails);
        }

        return true;
    }    
}
