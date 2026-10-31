package io.cranial.platform.application.idempotency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecordEntity> {
}

