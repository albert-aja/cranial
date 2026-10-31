package io.cranial.platform.application.featureflag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeatureFlagMapper extends BaseMapper<FeatureFlagEntity> {
}

