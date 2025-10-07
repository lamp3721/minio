package org.example.miniodemo.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.mapper.FileMetadataMapper;
import org.example.miniodemo.repository.FileMetadataRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link FileMetadataRepository} 接口的MyBatis-Plus实现。
 * <p>
 * 封装了所有基于 MyBatis-Plus 的数据库操作，将 ORM 框架的细节与业务逻辑隔离。
 */
@Repository // 使用@Repository注解标记这是一个数据访问组件
@RequiredArgsConstructor
@Slf4j
public class MyBatisFileMetadataRepository implements FileMetadataRepository {

    private final FileMetadataMapper fileMetadataMapper;

    @Override
    public boolean save(FileMetadata metadata) {
        return fileMetadataMapper.insert(metadata) > 0;
    }

    @Override
    public Optional<FileMetadata> findByHash(String hash, StorageType storageType) {
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getContentHash, hash)
                .eq(FileMetadata::getStorageType, storageType);
        FileMetadata fileMetadata = fileMetadataMapper.selectOne(queryWrapper);
        return Optional.ofNullable(fileMetadata);
    }

    @Override
    public void deleteByHash(String hash, StorageType storageType) {
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getContentHash, hash)
                .eq(FileMetadata::getStorageType, storageType);
        fileMetadataMapper.delete(queryWrapper);
    }

    @Override
    public List<FileMetadata> findAll(StorageType storageType) {
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getStorageType, storageType).orderByDesc(FileMetadata::getId);
        return fileMetadataMapper.selectList(queryWrapper);
    }

    @Override
    public Integer update(FileMetadata metadata) {
        log.info("正在更新文件元数据：{}", metadata);
        return fileMetadataMapper.updateById(metadata);
    }
}