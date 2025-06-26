package org.example.miniodemo.mapper;

import org.example.miniodemo.domain.FileMetadata;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 针对 `file_metadata` 表的MyBatis-Plus数据访问接口。
 * <p>
 * 通过继承 {@link BaseMapper}，自动拥有了对 {@link FileMetadata} 实体的基本CRUD（增删改查）功能。
 *
 * @author 29323
 * @createDate 2025-06-25 21:51:42
 * @Entity org.example.miniodemo.domain.FileMetadata
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

}




