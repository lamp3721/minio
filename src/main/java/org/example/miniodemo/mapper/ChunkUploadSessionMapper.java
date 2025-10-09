package org.example.miniodemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.miniodemo.domain.ChunkUploadSession;

/**
 * 分片上传会话Mapper接口
 */
@Mapper
public interface ChunkUploadSessionMapper extends BaseMapper<ChunkUploadSession> {
}