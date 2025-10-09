package org.example.miniodemo.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 文件存储类型枚举。
 */
@Getter
public enum StorageType {
    /**
     * 公共读存储桶。
     * <p>
     * 资源可以通过URL直接访问。
     */
    PUBLIC("PUBLIC", "public", "公共读"),
    /**
     * 私有读写存储桶。
     * <p>
     * 资源需要授权（如预签名URL）才能访问。
     */
    PRIVATE("PRIVATE", "private", "私有");

    @EnumValue // 标记数据库存的值是dbValue（与SQL枚举一致：大写）
    private final String dbValue;
    @JsonValue // 标记json返回的值是jsonValue（保持小写以兼容前端）
    private final String jsonValue;
    private final String description;

    StorageType(String dbValue, String jsonValue, String description) {
        this.dbValue = dbValue;
        this.jsonValue = jsonValue;
        this.description = description;
    }

    public String getCode() {
        return jsonValue;
    }
}