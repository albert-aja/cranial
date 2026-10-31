package io.cranial.platform.orchestration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.orchestration.entity.PromptEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PromptMapper extends BaseMapper<PromptEntity> {

    @Select("""
        SELECT * FROM ai_prompts
        WHERE prompt_code = #{promptCode}
          AND status = 'PUBLISHED'
        ORDER BY version DESC
        LIMIT 1
    """)
    Optional<PromptEntity> findPublishedByCode(@Param("promptCode") String promptCode);

    @Select("""
        SELECT * FROM ai_prompts
        WHERE prompt_code = #{promptCode}
        ORDER BY version DESC
    """)
    List<PromptEntity> findVersionsByCode(@Param("promptCode") String promptCode);
}

