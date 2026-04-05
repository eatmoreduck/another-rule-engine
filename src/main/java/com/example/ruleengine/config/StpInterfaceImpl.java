package com.example.ruleengine.config;

import cn.dev33.satoken.stp.StpInterface;
import com.example.ruleengine.domain.SysPermission;
import com.example.ruleengine.repository.SysPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色获取接口实现
 * 用于 @SaCheckPermission / @SaCheckRole 注解的校验
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysPermissionRepository sysPermissionRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        List<SysPermission> permissions = sysPermissionRepository.findPermissionsByUserId(userId);
        return permissions.stream()
                .map(SysPermission::getPermissionCode)
                .toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        return sysPermissionRepository.findRoleCodesByUserId(userId);
    }
}
