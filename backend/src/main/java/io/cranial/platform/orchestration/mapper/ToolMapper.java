package io.cranial.platform.orchestration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.orchestration.entity.ToolEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface ToolMapper extends BaseMapper<ToolEntity> {

    @Select("""
        SELECT * FROM ai_tools
        WHERE tool_code = #{toolCode}
          AND status = 'ACTIVE'
        LIMIT 1
    """)
    Optional<ToolEntity> findActiveByCode(@Param("toolCode") String toolCode);

    @Select("""
        SELECT * FROM ai_tools
        WHERE status = 'ACTIVE'
          AND (application_id IS NULL OR application_id = #{applicationId})
    """)
    List<ToolEntity> findAvailableForApplication(@Param("applicationId") UUID applicationId);
}

