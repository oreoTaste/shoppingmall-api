package com.tikitaka.api.batch.image;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface ImageSplittingBatchService {

	MultipartFile[] splitImages(MultipartFile[] imageFiles, int targetHeight) throws IOException;
	
}
