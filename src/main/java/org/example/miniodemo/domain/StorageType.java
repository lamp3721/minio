package org.example.miniodemo.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 文件存储类型枚举
 */
public enum StorageType {
    /**
     * 公开读
     */
    PUBLIC("PUBLIC"),
    /**
     * 私有
     */
    PRIVATE("PRIVATE");

    @EnumValue // 标记数据库存的值是code
    @JsonValue // 标记json返回的值是code
    private final String code;

    StorageType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
} 