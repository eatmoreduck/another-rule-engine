package com.example.ruleengine.service.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.example.ruleengine.domain.SysUserTeam;
import com.example.ruleengine.repository.SysPermissionRepository;
import com.example.ruleengine.repository.SysUserTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 数据权限服务
 * 提供当前用户的团队信息，用于数据过滤
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataPermissionService {

    private final SysUserTeamRepository sysUserTeamRepository;
    private final SysPermissionRepository sysPermissionRepository;

    /**
     * 获取当前登录用户所属的团队 ID 列表
     * 未登录或无团队时返回空列表
     */
    public List<Long> getCurrentUserTeamIds() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            List<SysUserTeam> userTeams = sysUserTeamRepository.findByUserId(userId);
            return userTeams.stream()
                    .map(SysUserTeam::getTeamId)
                    .toList();
        } catch (Exception e) {
            log.warn("获取当前用户团队ID失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 判断当前用户是否是超级管理员（ADMIN 角色）
     */
    public boolean isCurrentUserAdmin() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            List<String> roleCodes = sysPermissionRepository.findRoleCodesByUserId(userId);
            return roleCodes.contains("ADMIN");
        } catch (Exception e) {
            log.warn("判断当前用户是否管理员失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前登录用户ID
     */
    public Long getCurrentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
