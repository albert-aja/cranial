package io.cranial.platform.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.knowledge.entity.KnowledgeGlossaryTermEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeGlossaryTermMapper extends BaseMapper<KnowledgeGlossaryTermEntity> {
}

