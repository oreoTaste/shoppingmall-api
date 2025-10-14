package com.tikitaka.api.batch.image;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ImageDownloadBatchService {

	MultipartFile[] downloadImagesAsMultipartFiles(List<String> imageUrls) throws IOException;
}
