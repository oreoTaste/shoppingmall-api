package com.tikitaka.api.image;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ImageDownloadService {

	MultipartFile[] downloadImagesAsMultipartFiles(List<String> imageUrls);
}
