package org.example.miniodemo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

import java.io.Serializable;

/**
 * 对应数据库中的 `file_metadata` 表实体类。
 * <p>
 * 用于持久化存储每个已上传文件的核心元数据信息。
 *
 * @TableName file_metadata
 */
@TableName(value ="file_metadata")
@Data
public class FileMetadata implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件在存储桶中的完整对象名称/路径
     */
    private String objectName;

    /**
     * 文件的原始名称
     */
    private String originalFilename;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件的MIME类型
     */
    private String contentType;

    /**
     * 文件的内容哈希值（如MD5）
     */
    private String contentHash;

    /**
     * 文件所在的存储桶名称
     */
    private String bucketName;

    /**
     * 存储类型（PUBLIC 或 PRIVATE）
     */
    private StorageType storageType;

    /**
     * 记录创建时间
     */
    private Date createdAt;

    /**
     * 最后访问时间
     */
    private Date lastAccessedAt;

    /**
     * 文件被访问的次数
     */
    private Integer visitCount;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}