package io.cranial.platform.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.document.entity.DocumentExtractionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentExtractionMapper extends BaseMapper<DocumentExtractionEntity> {
}

