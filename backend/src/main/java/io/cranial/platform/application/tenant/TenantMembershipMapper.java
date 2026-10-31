package io.cranial.platform.application.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMembershipMapper extends BaseMapper<TenantMembershipEntity> {
}

