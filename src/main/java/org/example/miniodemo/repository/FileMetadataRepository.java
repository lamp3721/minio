package org.example.miniodemo.repository;

import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.domain.StorageType;

import java.util.List;
import java.util.Optional;

/**
 * 文件元数据仓储接口。
 * <p>
 * 定义了与文件元数据持久化相关的、与具体ORM框架无关的通用操作。
 * 这是业务逻辑与数据访问层之间的契约。
 */
public interface FileMetadataRepository {

    /**
     * 保存一个新的文件元数据记录。
     *
     * @param metadata 要保存的元数据对象。
     * @return 如果保存成功，返回true；否则返回false。
     */
    boolean save(FileMetadata metadata);

    /**
     * 根据内容哈希和存储类型查找文件元数据。
     *
     * @param hash        文件内容哈希。
     * @param storageType 存储类型 (PUBLIC 或 PRIVATE)。
     * @return 一个包含元数据的Optional，如果找不到则为空。
     */
    Optional<FileMetadata> findByHash(String hash, StorageType storageType);

    /**
     * 根据内容哈希和存储类型删除文件元数据。
     *
     * @param hash        文件内容哈希。
     * @param storageType 存储类型。
     */
    void deleteByHash(String hash, StorageType storageType);

    /**
     * 查找指定存储类型的所有文件元数据。
     *
     * @param storageType 存储类型。
     * @return 文件元数据列表。
     */
    List<FileMetadata> findAll(StorageType storageType);

    /**
     * 更新一个已存在的文件元数据记录。
     *
     * @param metadata 包含更新信息的元数据对象。
     * @return 受影响的行数。
     */
    Integer update(FileMetadata metadata);
}