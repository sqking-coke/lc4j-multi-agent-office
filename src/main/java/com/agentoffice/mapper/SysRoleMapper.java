package com.agentoffice.mapper;

import com.agentoffice.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    @Select("SELECT p.permission_code FROM sys_role_permission rp " +
            "JOIN sys_permission p ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId}")
    List<String> getPermissionCodesByRoleId(Long roleId);
}
