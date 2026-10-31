package io.cranial.platform.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.provider.entity.ModelCapabilityEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ModelCapabilityMapper extends BaseMapper<ModelCapabilityEntity> {

    @Select("SELECT capability_code FROM ai_model_capabilities WHERE model_id = #{modelId}")
    List<String> listByModelId(@Param("modelId") UUID modelId);

    @Delete("DELETE FROM ai_model_capabilities WHERE model_id = #{modelId}")
    int deleteByModelId(@Param("modelId") UUID modelId);
}

