package com.tikitaka.api.batch.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImageSplittingBatchServiceImpl implements ImageSplittingBatchService{

    /**
     * MultipartFile 배열로 받은 이미지들을 각각 일정한 높이로 분할합니다.
     * 분할된 이미지들은 파일로 저장되지 않고, MultipartFile[] 형태로 메모리상에서 반환됩니다.
     *
     * @param imageFiles 분할할 이미지 파일 배열
     * @param targetHeight 분할할 기준 높이 (pixel)
     * @return 분할된 이미지들의 MultipartFile 리스트
     * @throws IOException 이미지 처리 중 발생할 수 있는 예외
     */
	public MultipartFile[] splitImages(MultipartFile[] imageFiles, int targetHeight) throws IOException {
        List<MultipartFile> splitImageFiles = new ArrayList<>();

        if (imageFiles == null || imageFiles.length == 0) {
            return new MultipartFile[0];
        }

        for (MultipartFile file : imageFiles) {
            if (file == null || file.isEmpty()) continue;

            byte[] originalFileBytes = file.getBytes();
            BufferedImage originalImage = null;
            
            try {
                originalImage = ImageIO.read(new ByteArrayInputStream(originalFileBytes));
            } catch (Exception e) {
                log.warn("이미지 읽기 중 예외 발생 (건너뜀): {}", file.getOriginalFilename());
                continue;
            }

            // 이미지를 읽지 못한 경우(null), 원본을 추가하지 않고 로그를 남긴 뒤 건너뜁니다.
            if (originalImage == null) {
                log.warn("이미지 디코딩 실패 - 지원하지 않는 형식이거나 손상된 파일입니다. (건너뜀): {}", file.getOriginalFilename());
                continue;
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            String originalFileName = file.getOriginalFilename();
            String baseName = getBaseName(originalFileName);
            String extension = getFileExtension(originalFileName);

            // 확장자가 없거나 지원하지 않는 경우 'png'로 고정
            if (extension == null || extension.isBlank() || !isSupportedImageFormat(extension)) {
                extension = "png";
            }
            
            String realMimeType = getMimeTypeFromExtension(extension);

            // 1. 분할이 필요 없는 경우 (재인코딩하여 포맷 통일)
            if (originalHeight <= targetHeight) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    boolean result = ImageIO.write(originalImage, extension, baos);
                    
                    // ImageIO가 해당 확장자로 쓰기에 실패했을 경우 대비
                    if (!result) {
                        log.warn("이미지 인코딩 실패 (확장자: {}). PNG로 재시도합니다: {}", extension, originalFileName);
                        baos.reset();
                        ImageIO.write(originalImage, "png", baos);
                        realMimeType = "image/png";
                        extension = "png";
                    }
                    
                    baos.flush();
                    byte[] imageBytes = baos.toByteArray();
                    baos.close();

                    // 파일명이 .jpg인데 실제 데이터가 png가 되는 혼동을 막기 위해 파일명 확장자도 맞춤 (선택 사항)
                    // String finalFileName = baseName + "." + extension; 
                    MultipartFile processedFile = new InMemoryMultipartFile(originalFileName, realMimeType, imageBytes);
                    splitImageFiles.add(processedFile);
                } catch (IOException e) {
                    log.error("이미지 재인코딩 중 오류 발생 (건너뜀): {}", originalFileName, e);
                }
                continue;
            }

            // 2. 분할이 필요한 경우
            int numberOfParts = (int) Math.ceil((double) originalHeight / targetHeight);
            for (int i = 0; i < numberOfParts; i++) {
                int y = i * targetHeight;
                int h = Math.min(targetHeight, originalHeight - y);

                BufferedImage subImage = originalImage.getSubimage(0, y, originalWidth, h);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(subImage, extension, baos);
                baos.flush();
                byte[] imageBytes = baos.toByteArray();
                baos.close();

                String newFileName = String.format("%s_part%03d.%s", baseName, (i + 1), extension);
                MultipartFile newPartFile = new InMemoryMultipartFile(newFileName, realMimeType, imageBytes);
                splitImageFiles.add(newPartFile);
            }
        }
        return splitImageFiles.toArray(new MultipartFile[0]);
    }

    // 아래는 예시 헬퍼(helper) 메서드입니다. 실제 프로젝트의 구현에 맞게 사용하세요.
    private String getBaseName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > -1 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        }
        return "";
    }

    private boolean isSupportedImageFormat(String extension) {
        String[] supportedFormats = ImageIO.getWriterFormatNames();
        for (String format : supportedFormats) {
            if (format.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    // InMemoryMultipartFile 클래스가 없으시다면, 아래와 같이 간단하게 구현하여 테스트할 수 있습니다.
    // (실제 프로젝트에서는 더 견고하게 만들어야 합니다)
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.name;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            return this.content == null || this.content.length == 0;
        }

        @Override
        public long getSize() {
            return this.content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return this.content;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new java.io.ByteArrayInputStream(this.content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            new java.io.FileOutputStream(dest).write(this.content);
        }
    }
    
    // 확장자에 따른 올바른 MIME Type 반환 메서드
    private String getMimeTypeFromExtension(String extension) {
        if (extension == null) return "application/octet-stream";
        String ext = extension.toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            default:
                // Gemini는 image/* 타입을 원하므로 기본적으로 jpeg나 png로 유도하는 것이 안전함
                return "image/jpeg"; 
        }
    }
    
}
