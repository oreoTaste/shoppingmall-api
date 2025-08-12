package com.tikitaka.api.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImageSplittingServiceImpl implements ImageSplittingService{

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
            return new MultipartFile[0]; // 파일이 없으면 빈 배열 반환
        }

        // 각 MultipartFile에 대해 처리
        for (MultipartFile file : imageFiles) {
            if (file == null || file.isEmpty()) {
                continue; // 빈 파일은 건너뜁니다.
            }

            // --- [수정된 부분] ---
            // 1. InputStream을 직접 사용하는 대신, 파일의 모든 내용을 byte 배열로 먼저 읽습니다.
            //    이렇게 하면 데이터 스트림이 소모되는 문제를 근본적으로 방지할 수 있습니다.
            byte[] originalFileBytes = file.getBytes();

            // 2. byte 배열로부터 BufferedImage를 생성합니다.
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalFileBytes));

            if (originalImage == null) {
                // 혹은 지원하지 않는 파일 형식은 자르지 않고 바로 담기
                splitImageFiles.add(file);
                continue;
            }
            // --- [수정 끝] ---

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // 이미지가 자르기 기준 높이보다 작거나 같은 경우
            if (originalHeight <= targetHeight) {
                // --- [수정된 부분] ---
                // 원본 파일을 그대로 추가하는 대신, 읽어둔 byte 배열로 새로운 MultipartFile 객체를 만들어 추가합니다.
                // 이렇게 하면 반환되는 모든 파일 객체가 일관된 상태를 갖게 됩니다.
                MultipartFile newUnsplittedFile = new InMemoryMultipartFile(file.getOriginalFilename(), file.getContentType(), originalFileBytes);
                splitImageFiles.add(newUnsplittedFile);
                // --- [수정 끝] ---
                continue;
            }

            int numberOfParts = (int) Math.ceil((double) originalHeight / targetHeight);

            for (int i = 0; i < numberOfParts; i++) {
                int y = i * targetHeight;
                int h = Math.min(targetHeight, originalHeight - y);

                // 이미지 자르기
                BufferedImage subImage = originalImage.getSubimage(0, y, originalWidth, h);

                // 원본 파일 정보 가져오기
                String originalFileName = file.getOriginalFilename();
                String baseName = getBaseName(originalFileName);
                String extension = getFileExtension(originalFileName);

                // 확장자가 없는 경우 기본값으로 'png' 설정 (이미지 포맷 보장)
                if (extension == null || extension.isBlank() || !isSupportedImageFormat(extension)) {
                    extension = "png";
                }

                // 잘라낸 BufferedImage를 byte[]로 변환
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(subImage, extension, baos);
                baos.flush();
                byte[] imageBytes = baos.toByteArray();
                baos.close();

                // 새 파일 이름 생성 (예: originalName_part001.png)
                String newFileName = String.format("%s_part%03d.%s", baseName, (i + 1), extension);

                // byte[]를 InMemoryMultipartFile 객체로 변환하여 리스트에 추가
                MultipartFile newPartFile = new InMemoryMultipartFile(newFileName, file.getContentType(), imageBytes);
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
}
