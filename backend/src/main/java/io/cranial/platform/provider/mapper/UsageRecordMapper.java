package io.cranial.platform.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.provider.entity.UsageRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.UUID;

@Mapper
public interface UsageRecordMapper extends BaseMapper<UsageRecordEntity> {

    @Select("""
        SELECT
            COALESCE(COUNT(*), 0) AS total_requests,
            COALESCE(SUM(CASE WHEN is_local = TRUE THEN 1 ELSE 0 END), 0) AS local_requests,
            COALESCE(SUM(CASE WHEN is_local = FALSE THEN 1 ELSE 0 END), 0) AS hosted_requests,
            COALESCE(SUM(prompt_tokens), 0) AS total_prompt_tokens,
            COALESCE(SUM(completion_tokens), 0) AS total_completion_tokens,
            COALESCE(SUM(total_tokens), 0) AS total_tokens,
            COALESCE(SUM(estimated_cost_cents), 0) AS total_cost_cents,
            COALESCE(SUM(CASE WHEN status = 'FALLBACK_BLOCKED' THEN 1 ELSE 0 END), 0) AS fallback_blocked_count
        FROM ai_usage_records
        WHERE (#{applicationId} IS NULL OR application_id = #{applicationId})
    """)
    Map<String, Object> getUsageSummary(@Param("applicationId") UUID applicationId);
}

