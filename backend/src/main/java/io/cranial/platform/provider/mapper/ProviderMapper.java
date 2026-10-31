package io.cranial.platform.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.provider.entity.ProviderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderMapper extends BaseMapper<ProviderEntity> {
}

