package com.tikitaka.api.batch.image;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.api.batch.image.dto.UrlMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ImageDownloadBatchServiceImpl implements ImageDownloadBatchService{
    /**
     * 이미지 URL 리스트를 받아 MultipartFile 배열로 변환하여 반환합니다.
     * @param imageUrls 이미지 URL 목록
     * @return 변환된 MultipartFile 배열
     */
    public MultipartFile[] downloadImagesAsMultipartFiles(List<String> imageUrls) {
        List<MultipartFile> multipartFiles = new ArrayList<>();

        for (String imageUrl : imageUrls) {
            try {
                // URL에서 바이트 데이터 다운로드
                byte[] imageBytes = downloadImageBytes(imageUrl);

                // 원본 파일명 추출 (URL의 마지막 부분을 사용)
                String originalFileName = extractFileNameFromUrl(imageUrl);

                // MIME 타입 추측
                String contentType = HttpURLConnection.guessContentTypeFromName(originalFileName);
                if (contentType == null) {
                    contentType = "application/octet-stream"; // 알 수 없는 경우 기본값 사용
                }

                // UrlMultipartFile 객체 생성 및 리스트에 추가
                MultipartFile multipartFile = new UrlMultipartFile(imageBytes, originalFileName, contentType);
                multipartFiles.add(multipartFile);

            } catch (IOException e) {
                // 개별 이미지 다운로드 실패 시 로그를 남기고 계속 진행할 수 있습니다.
                // 또는 예외를 던져 전체 프로세스를 중단할 수도 있습니다.
                System.err.println("Failed to download image from URL: " + imageUrl);
                e.printStackTrace();
            }
        }

        return multipartFiles.toArray(new MultipartFile[0]);
    }

    /**
     * URL에 접속하여 데이터를 byte 배열로 다운로드합니다.
     */
    private byte[] downloadImageBytes(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // 일부 웹사이트는 User-Agent를 확인하므로 설정해주는 것이 좋습니다.
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        // AVIF > WebP > 원본 이미지 순으로 선호
        connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        
        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * URL의 경로 마지막 부분을 파일명으로 추출합니다.
     * 쿼리 파라미터가 있다면 제거합니다.
     */
    private String extractFileNameFromUrl(String imageUrl) {
        try {
            String path = new URL(imageUrl).getPath();
            // 쿼리 파라미터 제거
            int queryIndex = path.indexOf('?');
            if (queryIndex != -1) {
                path = path.substring(0, queryIndex);
            }
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            // URL 형식이 잘못된 경우를 대비하여 랜덤 UUID 기반의 파일명을 생성합니다.
            return UUID.randomUUID().toString() + ".jpg";
        }
    }
}
