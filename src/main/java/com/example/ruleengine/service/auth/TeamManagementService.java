package com.example.ruleengine.service.auth;

import com.example.ruleengine.domain.SysTeam;
import com.example.ruleengine.domain.SysUserTeam;
import com.example.ruleengine.repository.SysTeamRepository;
import com.example.ruleengine.repository.SysUserTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 团队管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamManagementService {

    private final SysTeamRepository sysTeamRepository;
    private final SysUserTeamRepository sysUserTeamRepository;

    /**
     * 获取所有团队
     */
    public List<SysTeam> listTeams() {
        return sysTeamRepository.findAll();
    }

    /**
     * 创建团队
     */
    @Transactional
    public SysTeam createTeam(String teamCode, String teamName, String description) {
        if (sysTeamRepository.existsByTeamCode(teamCode)) {
            throw new IllegalArgumentException("团队编码已存在: " + teamCode);
        }
        SysTeam team = SysTeam.builder()
                .teamCode(teamCode)
                .teamName(teamName)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        SysTeam saved = sysTeamRepository.save(team);
        log.info("创建团队: teamCode={}, teamName={}", teamCode, teamName);
        return saved;
    }

    /**
     * 更新团队
     */
    @Transactional
    public SysTeam updateTeam(Long teamId, String teamName, String description) {
        SysTeam team = sysTeamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("团队不存在: " + teamId));
        if (teamName != null) {
            team.setTeamName(teamName);
        }
        if (description != null) {
            team.setDescription(description);
        }
        team.setUpdatedAt(LocalDateTime.now());
        return sysTeamRepository.save(team);
    }

    /**
     * 删除团队
     */
    @Transactional
    public void deleteTeam(Long teamId) {
        // 先删除所有用户-团队关联
        sysUserTeamRepository.deleteByTeamId(teamId);
        sysTeamRepository.deleteById(teamId);
        log.info("删除团队: teamId={}", teamId);
    }

    /**
     * 分配用户到团队
     */
    @Transactional
    public void assignUserToTeam(Long userId, Long teamId) {
        if (sysUserTeamRepository.existsByUserIdAndTeamId(userId, teamId)) {
            return; // 已存在，跳过
        }
        SysUserTeam userTeam = SysUserTeam.builder()
                .userId(userId)
                .teamId(teamId)
                .createdAt(LocalDateTime.now())
                .build();
        sysUserTeamRepository.save(userTeam);
        log.info("分配用户到团队: userId={}, teamId={}", userId, teamId);
    }

    /**
     * 从团队移除用户
     */
    @Transactional
    public void removeUserFromTeam(Long userId, Long teamId) {
        sysUserTeamRepository.deleteByUserIdAndTeamId(userId, teamId);
        log.info("从团队移除用户: userId={}, teamId={}", userId, teamId);
    }

    /**
     * 获取团队的用户列表
     */
    public List<Long> getTeamUserIds(Long teamId) {
        return sysUserTeamRepository.findByTeamId(teamId).stream()
                .map(SysUserTeam::getUserId)
                .toList();
    }

    /**
     * 获取用户所属的团队列表
     */
    public List<SysTeam> getUserTeams(Long userId) {
        List<SysUserTeam> userTeams = sysUserTeamRepository.findByUserId(userId);
        List<Long> teamIds = userTeams.stream()
                .map(SysUserTeam::getTeamId)
                .toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return sysTeamRepository.findAllById(teamIds);
    }

    /**
     * 获取团队及其用户ID列表的映射
     */
    public Map<Long, List<Long>> getTeamUserMap() {
        List<SysUserTeam> allUserTeams = sysUserTeamRepository.findAll();
        return allUserTeams.stream()
                .collect(Collectors.groupingBy(
                        SysUserTeam::getTeamId,
                        Collectors.mapping(SysUserTeam::getUserId, Collectors.toList())
                ));
    }
}
