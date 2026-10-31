package io.cranial.platform.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PlatformUserMapper extends BaseMapper<PlatformUserEntity> {

    @Select("""
            SELECT r.code
            FROM roles r
            INNER JOIN platform_user_roles ur ON ur.role_id = r.id
            WHERE ur.platform_user_id = #{userId}
            ORDER BY r.code
            """)
    List<String> findRoleCodesByUserId(@Param("userId") UUID userId);

    @Select("""
            SELECT DISTINCT p.code
            FROM permissions p
            INNER JOIN role_permissions rp ON rp.permission_id = p.id
            INNER JOIN platform_user_roles ur ON ur.role_id = rp.role_id
            WHERE ur.platform_user_id = #{userId}
            ORDER BY p.code
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") UUID userId);

    @Select("SELECT id FROM roles WHERE code = #{roleCode}")
    UUID findRoleIdByCode(@Param("roleCode") String roleCode);

    @Insert("INSERT INTO platform_user_roles (platform_user_id, role_id) VALUES (#{userId}, #{roleId})")
    int assignRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
}

