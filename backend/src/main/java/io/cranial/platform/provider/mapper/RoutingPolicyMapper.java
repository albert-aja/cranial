package io.cranial.platform.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.provider.entity.RoutingPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface RoutingPolicyMapper extends BaseMapper<RoutingPolicyEntity> {

    @Select("""
        SELECT * FROM ai_routing_policies
        WHERE capability_code = #{capabilityCode}
          AND task_code = #{taskCode}
          AND is_active = TRUE
        LIMIT 1
    """)
    Optional<RoutingPolicyEntity> findSpecificPolicy(
            @Param("capabilityCode") String capabilityCode,
            @Param("taskCode") String taskCode
    );

    @Select("""
        SELECT * FROM ai_routing_policies
        WHERE capability_code = #{capabilityCode}
          AND task_code IS NULL
          AND is_active = TRUE
        LIMIT 1
    """)
    Optional<RoutingPolicyEntity> findDefaultPolicy(@Param("capabilityCode") String capabilityCode);
}

