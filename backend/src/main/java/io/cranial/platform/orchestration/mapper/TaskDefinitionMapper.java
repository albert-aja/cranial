package io.cranial.platform.orchestration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.cranial.platform.orchestration.entity.TaskDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface TaskDefinitionMapper extends BaseMapper<TaskDefinitionEntity> {

    @Select("""
        SELECT * FROM ai_task_definitions
        WHERE task_code = #{taskCode}
          AND is_active = TRUE
        LIMIT 1
    """)
    Optional<TaskDefinitionEntity> findActiveByCode(@Param("taskCode") String taskCode);
}

