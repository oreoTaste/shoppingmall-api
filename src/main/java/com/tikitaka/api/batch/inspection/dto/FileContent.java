// FileContent.java
package com.tikitaka.api.batch.inspection.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FileContent {
    private String originalFileName;
    private String mimeType;
    private byte[] content;
}