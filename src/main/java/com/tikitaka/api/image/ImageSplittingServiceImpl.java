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
	        if (file.isEmpty()) {
	            continue; // 빈 파일은 건너뜁니다.
	        }
	
	        BufferedImage originalImage = ImageIO.read(file.getInputStream());
	        int originalWidth = originalImage.getWidth();
	        int originalHeight = originalImage.getHeight();
	
	        // 이미지가 자르기 기준 높이보다 작거나 같으면, 원본 파일을 그대로 리스트에 추가
	        if (originalHeight <= targetHeight) {
	            splitImageFiles.add(file);
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
	
	            // 확장자가 없는 경우 기본값으로 'png' 설정
	            if (extension.isBlank()) {
	                extension = "png";
	            }
	
	            // 잘라낸 BufferedImage를 byte[]로 변환
	            ByteArrayOutputStream baos = new ByteArrayOutputStream();
	            ImageIO.write(subImage, extension, baos);
	            baos.flush();
	            byte[] imageBytes = baos.toByteArray();
	            baos.close();
	
	            // 새 파일 이름 생성 (예: originalName_part1.png)
	            String newFileName = String.format("%s_part%03d.%s", baseName, (i + 1), extension);
	
	            // byte[]를 InMemoryMultipartFile 객체로 변환하여 리스트에 추가
	            MultipartFile newPartFile = new InMemoryMultipartFile(newFileName, file.getContentType(), imageBytes);
	            splitImageFiles.add(newPartFile);
	        }
	    }

	    return splitImageFiles.toArray(new MultipartFile[0]);
	}



    /**
     * 파일 이름에서 확장자를 추출합니다.
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 파일 이름에서 확장자를 제외한 기본 이름을 추출합니다.
     */
    private String getBaseName(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }
    
	
	/**
	 * byte[] 데이터를 기반으로 MultipartFile 인터페이스를 구현하는 클래스.
	 * 분할된 이미지를 메모리 상에서 MultipartFile 객체로 다루기 위해 사용됩니다.
	 */
	class InMemoryMultipartFile implements MultipartFile {
	
	    private final String name;
	    private final String originalFilename;
	    private final String contentType;
	    private final byte[] content;
	
	    public InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
	        this.name = "file"; // 'name' 속성은 폼 필드의 이름에 해당하며, 필요에 따라 변경 가능
	        this.originalFilename = originalFilename;
	        this.contentType = contentType;
	        this.content = content;
	    }
	
	    @Override
	    public String getName() {
	        return this.name;
	    }
	
	    @Override
	    public String getOriginalFilename() {
	        return this.originalFilename;
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
	    public InputStream getInputStream() throws IOException {
	        return new ByteArrayInputStream(this.content);
	    }
	
	    @Override
	    public void transferTo(File dest) throws IOException, IllegalStateException {
	        throw new UnsupportedOperationException("This operation is not supported for in-memory files.");
	    }
	}

}
