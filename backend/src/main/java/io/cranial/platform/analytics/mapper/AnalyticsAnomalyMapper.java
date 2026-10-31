package io.cranial.platform.analytics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.analytics.entity.AnalyticsAnomalyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalyticsAnomalyMapper extends BaseMapper<AnalyticsAnomalyEntity> {
}

