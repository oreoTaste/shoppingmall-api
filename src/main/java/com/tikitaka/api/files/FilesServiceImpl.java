package com.tikitaka.api.files;

import com.tikitaka.api.files.entity.Files;
import com.tikitaka.api.goods.entity.Goods;
import com.tikitaka.api.inspection.dto.FileContent;
import com.tikitaka.api.member.dto.CustomUserDetails;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional // 클래스 레벨에서 트랜잭션을 적용하여 모든 공용 메서드에 트랜잭션이 적용됩니다.
@RequiredArgsConstructor // [수정] 이 어노테이션만 남겨둡니다.
public class FilesServiceImpl implements FilesService {

    private final FilesRepository filesRepository;

    // [수정] @Value 어노테이션을 필드에 직접 적용합니다.
    @Value("${file.upload-dir}")
    private String uploadDir;
    
    // [수정] Path 객체는 final을 제거하고, 초기화 로직을 분리합니다.
    private Path fileStorageLocation;
    
    // [수정] @PostConstruct를 사용하여 의존성 주입 후 초기화 로직을 실행합니다.
    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(this.uploadDir).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("파일을 업로드할 디렉토리를 생성할 수 없습니다.", ex);
        }
    }

    /**
     * 여러 개의 파일을 사용자 ID와 연도별 폴더에 나누어 저장합니다.
     * @param goods 연관된 상품 엔티티
     * @param files 저장할 파일들의 배열
     * @param userDetails 현재 인증된 사용자 정보
     * @throws IOException 파일 저장 중 오류 발생 시
     */
    @Override
    @Transactional
    public void save(Goods goods, MultipartFile[] files, CustomUserDetails userDetails) throws IOException {
        if (files == null || files.length == 0 || (files.length == 1 && files[0].isEmpty())) {
            log.info("첨부된 파일이 없습니다.");
            return;
        }

        // 사용자 ID와 현재 연도를 기반으로 하위 디렉토리 경로를 생성합니다.
        Long memberId = userDetails.getMemberId();
        int currentYear = LocalDate.now().getYear();
        String subPath = memberId + "/" + currentYear; // 예: "3/2025"

        // 실제 파일을 저장할 전체 디렉토리 경로를 생성합니다.
        Path targetDirectory = this.fileStorageLocation.resolve(subPath).normalize();
        
        // 디렉토리가 존재하지 않으면 생성합니다.
        if (!java.nio.file.Files.exists(targetDirectory)) {
            java.nio.file.Files.createDirectories(targetDirectory);
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }

            // 고유한 파일명 생성
            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
            String fileExtension = "";
            try {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            } catch (Exception e) {
                log.warn("파일에 확장자가 없습니다: {}", originalFileName);
            }
            String storedFileName = UUID.randomUUID().toString() + fileExtension;
            
            // 최종 파일 저장 경로를 연도별 폴더 안으로 지정합니다.
            Path targetLocation = targetDirectory.resolve(storedFileName);
            try (InputStream inputStream = file.getInputStream()) {
                java.nio.file.Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            // DB에 저장할 웹 경로도 연도별 폴더 구조를 포함하도록 수정합니다.
            String webAccessiblePath = "/uploads/" + subPath + "/" + storedFileName; // 예: /uploads/3/2025/uuid.jpg
            
            Files newFile = new Files(webAccessiblePath, originalFileName, goods.getGoodsId(), memberId, memberId);
            
            filesRepository.save(newFile);
            
            log.info("파일이 성공적으로 저장되었습니다. 경로: {}", webAccessiblePath);
        }
    }
    
    /**
     * [추가] goodsId를 기준으로 파일 목록을 조회하는 서비스 메소드
     */
    @Override
    public List<Files> findByGoodsId(Long goodsId) {
        log.info("goodsId '{}'에 대한 파일 목록을 조회합니다.", goodsId);
        return filesRepository.findByGoodsId(goodsId);
    }    
    

    /**
     * DB 파일 정보를 기반으로 실제 파일 내용을 읽어와 FileContent 리스트로 반환하는 메소드
     */
    @Override
    public List<FileContent> readFiles(List<Files> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }

        List<FileContent> fileContents = new ArrayList<>();
        for (Files dbFile : files) {
            try {
                String relativePath = dbFile.getFilePath().startsWith("/uploads/") 
                                      ? dbFile.getFilePath().substring("/uploads/".length()) 
                                      : dbFile.getFilePath();

                Path absoluteFilePath = this.fileStorageLocation.resolve(relativePath).normalize();
                
                if (java.nio.file.Files.exists(absoluteFilePath)) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(absoluteFilePath);
                    
                    // [핵심 수정] 확장자를 기반으로 MIME 타입을 결정하는 헬퍼 메소드 호출
                    String mimeType = getMimeType(dbFile.getFileName(), absoluteFilePath);

                    fileContents.add(new FileContent(dbFile.getFileName(), mimeType, bytes));
                } else {
                    log.warn("파일을 찾을 수 없습니다: {}", absoluteFilePath.toString());
                }
            } catch (IOException e) {
                log.error("파일({})을 읽는 중 오류 발생", dbFile.getFilePath(), e);
            }
        }
        return fileContents;
    }

    /**
     * [추가] 파일명과 경로를 기반으로 더 정확한 MIME 타입을 반환하는 헬퍼 메소드
     */
    private String getMimeType(String originalFileName, Path filePath) throws IOException {
        // 1. 파일 확장자를 소문자로 추출
        Optional<String> extension = Optional.ofNullable(originalFileName)
            .filter(f -> f.contains("."))
            .map(f -> f.substring(originalFileName.lastIndexOf(".") + 1).toLowerCase());

        // 2. 확장자에 따라 알려진 MIME 타입을 직접 매핑 (AVIF, HEIC 등 최신 형식 추가)
        if (extension.isPresent()) {
            switch (extension.get()) {
                case "avif":
                    return "image/avif";
                case "heic":
                    return "image/heic";
                case "heif":
                    return "image/heif";
                case "webp":
                    return "image/webp";
                // 필요한 다른 타입들을 여기에 추가할 수 있습니다.
            }
        }
        
        // 3. 위에서 매핑되지 않은 경우, Java의 기본 감지 기능을 시도
        String probedMimeType = java.nio.file.Files.probeContentType(filePath);
        if (probedMimeType != null) {
            return probedMimeType;
        }

        // 4. 모든 방법으로도 찾을 수 없는 경우, 최종 기본값 반환
        return "application/octet-stream";
    }
    
    /*
     * [구현] goodsId에 해당하는 물리적 파일들을 삭제합니다.
     */
    @Override
    public boolean delete(Long goodsId) {
        return filesRepository.deleteByGoodsId(goodsId);
    }    
    
    /**
     * [구현] goodsId에 해당하는 물리적 파일들을 삭제합니다.
     */
    @Override
    public void deleteFilesByGoodsId(Long goodsId) {
        // 1. DB에서 삭제할 파일들의 정보를 조회합니다.
        List<Files> filesToDelete = filesRepository.findByGoodsId(goodsId);

        if (filesToDelete.isEmpty()) {
            log.info("goodsId '{}'에 삭제할 파일이 없습니다.", goodsId);
            return;
        }

        // 2. 각 파일 정보에 대해 물리적 파일을 삭제합니다.
        for (Files file : filesToDelete) {
            try {
                // DB에 저장된 웹 경로에서 실제 파일 시스템 경로를 구성합니다.
                String pathWithoutUploads = file.getFilePath().replaceFirst("/uploads/", "");
                Path filePath = this.fileStorageLocation.resolve(pathWithoutUploads).normalize();

                // 파일이 존재하면 삭제합니다.
                if (java.nio.file.Files.exists(filePath)) {
                    java.nio.file.Files.delete(filePath);
                    log.info("파일 삭제 성공: {}", filePath);
                } else {
                    log.warn("삭제할 파일을 찾을 수 없습니다: {}", filePath);
                }
            } catch (IOException e) {
                // 특정 파일 삭제 실패 시, 로그만 남기고 다음 파일로 진행합니다.
                // 또는 예외를 던져서 전체 트랜잭션을 롤백할 수도 있습니다.
                log.error("파일({})을 삭제하는 중 오류 발생", file.getFilePath(), e);
            }
        }
        // DB의 files 레코드는 ON DELETE CASCADE에 의해 goods 레코드 삭제 시 자동으로 삭제됩니다.
    }    
}
