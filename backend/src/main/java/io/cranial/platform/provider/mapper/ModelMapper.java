package io.cranial.platform.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.provider.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ModelMapper extends BaseMapper<ModelEntity> {

    @Select("""
        SELECT m.* FROM ai_models m
        INNER JOIN ai_model_capabilities c ON m.id = c.model_id
        WHERE c.capability_code = #{capabilityCode} AND m.status = 'ACTIVE'
        ORDER BY m.is_local DESC, m.created_at ASC
    """)
    List<ModelEntity> findActiveByCapability(@Param("capabilityCode") String capabilityCode);

    @Select("""
        SELECT capability_code FROM ai_model_capabilities WHERE model_id = #{modelId}
    """)
    List<String> findCapabilitiesByModelId(@Param("modelId") UUID modelId);
}

