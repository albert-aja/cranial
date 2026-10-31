package io.cranial.platform.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.knowledge.entity.KnowledgeFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeFeedbackMapper extends BaseMapper<KnowledgeFeedbackEntity> {
}

