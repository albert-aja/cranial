package io.cranial.platform.orchestration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.orchestration.entity.OrchestratorTraceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrchestratorTraceMapper extends BaseMapper<OrchestratorTraceEntity> {
}

