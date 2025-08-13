package com.tikitaka.api.goods;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.files.FilesService;
import com.tikitaka.api.files.entity.Files;
import com.tikitaka.api.goods.dto.GoodsInspectRequestDto;
import com.tikitaka.api.goods.dto.GoodsListDto;
import com.tikitaka.api.goods.dto.GoodsRegisterRequestDto;
import com.tikitaka.api.goods.dto.GoodsUpdateRequestDto;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.image.ImageDownloadService;
import com.tikitaka.api.image.ImagePathExtractor;
import com.tikitaka.api.image.ImageSplittingService;
import com.tikitaka.api.inspection.InspectService;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.inspection.dto.InspectionResult;
import com.tikitaka.api.member.dto.CustomUserDetails;

import ch.qos.logback.core.util.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
    private final ImageSplittingService imageSplittingService;
    private final InspectService inspectService;
    private final ImageDownloadService imageDownloadService;

    /**
     * GoodsRepository를 주입받는 생성자.
     * Spring이 이 서비스를 생성할 때 자동으로 GoodsRepository 빈을 찾아 주입합니다.
     * @param goodsRepository 상품 데이터 접근을 위한 Repository
     */
    public GoodsServiceImpl(GoodsRepository goodsRepository, FilesService filesService, ImageSplittingService imageSplittingService, InspectService inspectService, ImageDownloadService imageDownloadService) {
        this.goodsRepository = goodsRepository;
        this.filesService = filesService;
        this.imageSplittingService = imageSplittingService;
        this.inspectService = inspectService;
        this.imageDownloadService = imageDownloadService;
    }
    
    /**
     * [신규 구현] 상품 검수 로직을 서비스 계층에서 처리합니다.
     */
    @Override
    @Transactional
    public InspectionResult inspectNewGoods(GoodsInspectRequestDto request, CustomUserDetails userDetails) throws IOException {
        // 1. 요청 DTO를 Goods 엔티티로 변환
        Goods goods = request.toEntity(userDetails);

        // 2. 검수에 필요한 파일 목록 준비 (기존 Controller의 private 메서드 로직 이동)
        List<FileContent> filesToInspect = prepareInspectionFiles(
            goods.getGoodsId(),
            request.getRepresentativeFile(),
            request.getImageFiles(),
            request.getImageHtml(),
            request.getImageType()
        );

        // 3. 외부 AI 검수 서비스 호출
        InspectionResult result = inspectService.inspectGoodsInfoWithPhotos(goods, filesToInspect);

        // 4. 검수 승인 시 후속 처리
        if (result.isApproved()) {
            handleApprovedInspection(goods);
        }

        return result;
    }    

    
    @Override
    @Transactional
    public Goods registerGoods(GoodsRegisterRequestDto request, CustomUserDetails userDetails) throws IOException {
        Goods newGoods = new Goods(
            request.getGoodsName(),
            request.getMobileGoodsName(),
            Long.valueOf(request.getSalesPrice()),
            Long.valueOf(request.getBuyPrice()),
            request.getOrigin() == null || request.getOrigin().isEmpty() ? "국내산" : request.getOrigin(),
            userDetails.getMemberId(),
            userDetails.getMemberId()
        );
        newGoods.setAiCheckYn(request.getAiCheckYn() == null || request.getAiCheckYn().isEmpty() ? "N" : request.getAiCheckYn());

        Goods savedGoods = goodsRepository.save(newGoods);

        // 대표 이미지 저장
        filesService.save(savedGoods, request.getRepresentativeFile(), userDetails, true);

        // 상세 정보 저장 (HTML 또는 파일)
        if ("html".equals(request.getImageType())) {
            filesService.save(savedGoods, request.getImageHtml(), userDetails);
        } else {
            MultipartFile[] splittedImages = imageSplittingService.splitImages(request.getImageFiles(), 1600);
            filesService.save(savedGoods, splittedImages, userDetails);
        }

        return savedGoods;
    }

    @Override
    @Transactional
    public boolean updateGoods(GoodsUpdateRequestDto request, CustomUserDetails userDetails) throws IOException {
        Goods goodsToUpdate = new Goods(
                request.getGoodsId(),
                request.getGoodsName(),
                request.getMobileGoodsName(),
                Long.valueOf(request.getSalesPrice()),
                Long.valueOf(request.getBuyPrice()),
                request.getOrigin(),
                null, // insertId는 업데이트하지 않음
                userDetails.getMemberId() // updateId 설정
        );

        if ("html".equals(request.getImageType())) {
            return this.updateWithFiles(goodsToUpdate, request.getRepresentativeFile(), request.getImageHtml(), userDetails);
        } else {
            return this.updateWithFiles(goodsToUpdate, request.getRepresentativeFile(), request.getImageFiles(), userDetails);
        }
    } 
    
    // Controller에서 가져온 private 헬퍼 메서드들
    private List<FileContent> prepareInspectionFiles(Long goodsId, MultipartFile[] representativeFile, MultipartFile[] imageFiles, String imageHtml, String imageType) throws IOException {
        List<FileContent> inspectionFiles = new ArrayList<>();

        boolean hasNewRepresentative = representativeFile != null && representativeFile.length > 0 && !representativeFile[0].isEmpty();
        boolean hasNewImages = (imageFiles != null && imageFiles.length > 0 && !imageFiles[0].isEmpty()) || !StringUtil.isNullOrEmpty(imageHtml);

        // 1. 대표 이미지 처리
        if (hasNewRepresentative) {
            inspectionFiles.addAll(convertMultipartToContent(representativeFile));
        } else if (goodsId != null) {
            List<Files> dbFiles = filesService.findByGoodsId(goodsId);
            dbFiles.stream()
                .filter(Files::isRepresentativeYn)
                .findFirst()
                .ifPresent(file -> {
                    try {
                        inspectionFiles.addAll(filesService.readFiles(List.of(file)));
                    } catch (IOException e) {
                        throw new RuntimeException("기존 대표 파일을 읽는 중 오류 발생", e);
                    }
                });
        }

        // 2. 상세 이미지 처리
        if (hasNewImages) {
            if ("html".equals(imageType)) {
                List<String> imageUrls = ImagePathExtractor.extractImageUrls(imageHtml);
                MultipartFile[] downloadedImages = imageDownloadService.downloadImagesAsMultipartFiles(imageUrls);
                inspectionFiles.addAll(convertMultipartToContent(downloadedImages));
            } else { // "file"
                MultipartFile[] splittedImages = imageSplittingService.splitImages(imageFiles, 1600);
                inspectionFiles.addAll(convertMultipartToContent(splittedImages));
            }
        } else if (goodsId != null) {
            List<Files> dbFiles = filesService.findByGoodsId(goodsId);
            List<Files> detailFiles = dbFiles.stream().filter(f -> !f.isRepresentativeYn()).toList();
            inspectionFiles.addAll(filesService.readFiles(detailFiles));
        }

        return inspectionFiles;
    }

    private void handleApprovedInspection(Goods goods) {
        if (goods.getGoodsId() != null) {
            goods.setAiCheckYn("Y");
            updateAiCheckYn(goods);
        }
    }

    private List<FileContent> convertMultipartToContent(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<FileContent> contents = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                contents.add(new FileContent(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            }
        }
        return contents;
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

    @Transactional
    public boolean updateGoods(Goods goods) throws IOException {
        // 1. 상품 정보(텍스트)를 먼저 업데이트합니다.
        boolean isGoodsUpdated = goodsRepository.update(goods);
        if (!isGoodsUpdated) {
            log.warn("업데이트할 상품을 찾지 못했습니다. ID: {}", goods.getGoodsId());
            return false;
        }
        return true;
        
    }
    
    
    /**
     * [구현] 상품 정보와 파일을 함께 업데이트하는 트랜잭션 메소드
     */
    @Override
    @Transactional
    public boolean updateWithFiles(Goods goods, MultipartFile[] representativeFiles, MultipartFile[] imageFiles, CustomUserDetails userDetails) throws IOException {
        // 1. 상품 정보(텍스트)를 먼저 업데이트합니다.
        boolean isGoodsUpdated = goodsRepository.update(goods);
        if (!isGoodsUpdated) {
            log.error("상품 업데이트 실패: ID {}에 해당하는 상품을 찾지 못했습니다.", goods.getGoodsId());
            return false;
        }

        try {
            // 2. 새로운 파일이 첨부되었는지 확인합니다.
            boolean hasNewRepresentativeFiles = representativeFiles != null && representativeFiles.length > 0 && !representativeFiles[0].isEmpty();
            boolean hasNewImageFiles = imageFiles != null && imageFiles.length > 0 && !imageFiles[0].isEmpty();

            // 3. 새로운 파일이 있다면 기존 파일을 삭제하고 새 파일을 저장합니다.
            if (hasNewRepresentativeFiles) {
                // 기존 대표 파일 삭제 (물리적 파일 및 DB 레코드)
                deleteRepresentativeFilesByGoodsId(goods.getGoodsId());
                // 새 대표 파일 저장
                filesService.save(goods, representativeFiles, userDetails, true); // true는 대표 파일임을 나타내는 플래그
            }

            if (hasNewImageFiles) {
                // 기존 상세 파일 삭제 (물리적 파일 및 DB 레코드)
                deleteImageFilesByGoodsId(goods.getGoodsId());
                
                // 파일의 세로길이가 너무 클 경우 자른다.
                MultipartFile[] splittedImageFiles = this.imageSplittingService.splitImages(imageFiles, 1600);
                
                // 새 상세 파일 저장
                filesService.save(goods, splittedImageFiles, userDetails, false); // false는 상세 파일임을 나타내는 플래그
            }
        } catch (Exception e) {
            // 파일 처리 중 예상치 못한 오류 발생 시 트랜잭션 롤백과 함께 로그를 남깁니다.
            log.error("상품 ID {}의 파일 업데이트 중 오류가 발생했습니다.", goods.getGoodsId(), e);
            // 트랜잭션이 롤백될 것이므로 여기서 false를 반환해도 됩니다.
            return false;
        }
        
        return true;
    }

    private void deleteRepresentativeFilesByGoodsId(Long goodsId) {
        try {
            List<Files> oldFiles = filesService.findByGoodsId(goodsId);
            for (Files oldFile : oldFiles) {
                if (oldFile.isRepresentativeYn()) {
                    // db에서 파일 삭제
                    filesService.deleteByFilesId(oldFile.getFilesId());
                    // 서버 저장된 파일 데이터 삭제
                    filesService.deleteFilesByFilesId(oldFile.getFilesId());
                }
            }
        } catch (Exception e) {
            log.error("상품 ID {}의 대표 파일 삭제 중 오류가 발생했습니다.", goodsId, e);
            // 오류가 발생해도 트랜잭션이 롤백되도록 예외를 다시 던질 수 있습니다.
            throw new RuntimeException("대표 파일 삭제 실패", e);
        }
    }

    private void deleteImageFilesByGoodsId(Long goodsId) {
        try {
            List<Files> oldFiles = filesService.findByGoodsId(goodsId);
            for (Files oldFile : oldFiles) {
                if (!oldFile.isRepresentativeYn()) {
                    // db에서 파일 삭제
                    filesService.deleteByFilesId(oldFile.getFilesId());
                    // 서버 저장된 파일 데이터 삭제
                    filesService.deleteByFilesId(oldFile.getFilesId());
                }
            }
        } catch (Exception e) {
            log.error("상품 ID {}의 상세 파일 삭제 중 오류가 발생했습니다.", goodsId, e);
            // 오류가 발생해도 트랜잭션이 롤백되도록 예외를 다시 던질 수 있습니다.
            throw new RuntimeException("상세 파일 삭제 실패", e);
        }
    }
    
    /**
     * [구현] 상품 정보와 파일을 함께 업데이트하는 트랜잭션 메소드
     */
    @Override
    @Transactional
    public boolean updateWithFiles(Goods goods, MultipartFile[] representativeFiles, String imageTag, CustomUserDetails userDetails) throws IOException {
        // 1. 상품 정보(텍스트)를 먼저 업데이트합니다.
        boolean isGoodsUpdated = goodsRepository.update(goods);
        if (!isGoodsUpdated) {
            log.warn("업데이트할 상품을 찾지 못했습니다. ID: {}", goods.getGoodsId());
            return false;
        }

        // 2. 새로운 파일이 첨부되었는지 확인합니다.
        boolean hasNewRepresentativeFiles = representativeFiles != null && representativeFiles.length > 0 && !representativeFiles[0].isEmpty();
        boolean hasNewTag = imageTag != null && !StringUtil.isNullOrEmpty(imageTag);
        
        // 3. 새로운 파일이 있다면 기존 파일을 삭제하고 새 파일을 저장합니다.
        if (hasNewRepresentativeFiles) {
            // 기존 대표 파일 삭제 (물리적 파일 및 DB 레코드)
            deleteRepresentativeFilesByGoodsId(goods.getGoodsId());
            // 새 대표 파일 저장
            filesService.save(goods, representativeFiles, userDetails, true);
        }

        if (hasNewTag) {
            // 기존 상세 파일 삭제 (물리적 파일 및 DB 레코드)
            deleteImageFilesByGoodsId(goods.getGoodsId());
            
            // 새 상세 파일 저장
            filesService.save(goods, imageTag, userDetails); // false는 상세 파일임을 나타내는 플래그
        }
        
        return true;
    }

    @Override
    public boolean updateAiCheckYn(Goods goods) {
        // 1. Retrieve the existing Goods entity from the database using goodsId.
        //    You'll need a method in your goodsRepository to find a Goods by its ID.
        Goods goodsToUpdate = goodsRepository.findById(goods.getGoodsId()); // Assuming findById exists

        System.out.println("goodsToUpdate" + goodsToUpdate);
        if (goodsToUpdate != null) {
            // 2. Set the new aiCheckYn value.
            goodsToUpdate.setAiCheckYn(goods.getAiCheckYn());

            // 3. Update the Goods entity in the database.
            //    This update method in your repository should persist the changes.
            boolean isGoodsUpdated = goodsRepository.update(goodsToUpdate);
            return isGoodsUpdated;
        }
        // If the goods with the given ID was not found, return false.
        return false;
    }}
