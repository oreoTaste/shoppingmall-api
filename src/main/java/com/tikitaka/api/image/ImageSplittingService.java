package com.tikitaka.api.image;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ImageSplittingService {

	MultipartFile[] splitImages(MultipartFile[] imageFiles, int targetHeight) throws IOException;
	
}
