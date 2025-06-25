package org.example.miniodemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.miniodemo.domain.FileMetadata;

import org.example.miniodemo.mapper.FileMetadataMapper;
import org.example.miniodemo.service.FileMetadataService;
import org.springframework.stereotype.Service;

/**
* @author 29323
* @description 针对表【file_metadata(MinIO文件元数据表)】的数据库操作Service实现
* @createDate 2025-06-25 21:51:42
*/
@Service
public class FileMetadataServiceImpl extends ServiceImpl<FileMetadataMapper, FileMetadata>
    implements FileMetadataService {

}




