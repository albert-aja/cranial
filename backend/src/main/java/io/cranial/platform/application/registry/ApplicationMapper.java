package io.cranial.platform.application.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface ApplicationMapper extends BaseMapper<ApplicationEntity> {

    @Select("SELECT * FROM applications WHERE code = #{code} LIMIT 1")
    Optional<ApplicationEntity> findByCode(@Param("code") String code);
}


