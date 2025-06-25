package org.example.miniodemo.dto;

import lombok.Data;

@Data
public class CheckRequestDto {
    private String fileHash;
    private String fileName;
} 