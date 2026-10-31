package io.cranial.platform.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.decision.entity.DecisionRecommendationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DecisionRecommendationMapper extends BaseMapper<DecisionRecommendationEntity> {
}

