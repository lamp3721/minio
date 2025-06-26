package org.example.miniodemo.domain;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * 通用的存储对象元数据表示。
 * <p>
 * 这是一个DTO，用于在存储服务和业务服务之间传递对象信息，
 * 避免了业务层对具体存储提供商（如MinIO）的SDK类的直接依赖。
 */
@Data
@Builder
public class StorageObject {
    /**
     * 对象在存储桶中的完整路径和名称。
     */
    private String objectName;

    /**
     * 对象的最后修改时间。
     */
    private ZonedDateTime lastModified;

    /**
     * 对象的大小（以字节为单位）。
     */
    private long size;
} 