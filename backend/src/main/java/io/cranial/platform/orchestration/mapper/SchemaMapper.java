package io.cranial.platform.orchestration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface SchemaMapper extends BaseMapper<SchemaEntity> {

    @Select("SELECT * FROM ai_schemas WHERE schema_code = #{schemaCode} AND status = 'ACTIVE' LIMIT 1")
    Optional<SchemaEntity> findByCode(@Param("schemaCode") String schemaCode);

    default Optional<SchemaEntity> findActiveByCode(String schemaCode) {
        return findByCode(schemaCode);
    }
}
