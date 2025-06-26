package org.example.miniodemo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

import java.io.Serializable;

/**
 * MinIO文件元数据表
 * @TableName file_metadata
 */
@TableName(value ="file_metadata")
@Data
public class FileMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 记录的唯一ID，主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件在MinIO中存储的唯一名称
     */
    private String objectName;

    /**
     * 用户上传时文件的原始名称
     */
    private String originalFilename;

    /**
     * 文件的大小，单位是字节（Bytes）
     */
    private Long fileSize;

    /**
     * 文件的MIME类型
     */
    private String contentType;

    /**
     * 文件内容的MD5哈希值
     */
    private String contentHash;

    /**
     * 文件所在的MinIO存储桶的名称
     */
    private String bucketName;

    /**
     * 存储类型（PUBLIC 或 PRIVATE）
     */
    private StorageType storageType;

    /**
     * （预留）关联的用户ID
     */
    private Long userId;

    /**
     * 记录的创建时间
     */
    private Date createdAt;

    /**
     * 文件最后一次被访问的时间
     */
    private Date lastAccessedAt;
}