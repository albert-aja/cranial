package io.cranial.platform.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.analytics.entity.AnalyticsQueryCacheEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalyticsQueryCacheMapper extends BaseMapper<AnalyticsQueryCacheEntity> {
}

