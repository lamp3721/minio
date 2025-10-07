package org.example.miniodemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the response of a chunk upload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadResponseDto {

    /**
     * The number of the uploaded chunk.
     */
    private Integer chunkNumber;

    /**
     * The path of the uploaded chunk.
     */
    private String chunkPath;
}