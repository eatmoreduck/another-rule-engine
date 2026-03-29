package com.example.ruleengine.validator;

import com.example.ruleengine.constants.RuleStatus;
import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.RuleRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则使用检查器
 * 检查规则是否在使用中（是否处于启用且生效状态，是否有灰度配置或其他规则依赖）
 *
 * <p>当前为 Phase 2 基本实现：
 * <ul>
 *   <li>isInUse: 检查规则是否处于 ACTIVE 且已启用</li>
 *   <li>getDependentRules: 返回空列表（Phase 6 实现依赖分析后增强）</li>
 *   <li>getUsageInfo: 返回基本使用信息（灰度检查暂返回空）</li>
 * </ul>
 *
 * <p>TODO: Phase 5 实现灰度发布后，检查是否有灰度配置
 * TODO: Phase 6 实现依赖分析后，检查是否有其他规则依赖
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleUsageChecker {

  private final RuleRepository ruleRepository;

  /**
   * 检查规则是否在使用中
   *
   * <p>当前实现：检查规则是否处于 ACTIVE 状态且已启用。
   * 后续增强：
   * <ul>
   *   <li>检查是否有灰度配置引用此规则</li>
   *   <li>检查是否有其他规则依赖此规则</li>
   *   <li>检查是否有正在执行的规则实例</li>
   * </ul>
   *
   * @param ruleKey 规则Key
   * @return true 如果规则正在使用中，false 否则
   */
  public boolean isInUse(String ruleKey) {
    try {
      Rule rule = ruleRepository.findByRuleKey(ruleKey).orElse(null);
      if (rule == null) {
        log.debug("规则不存在: ruleKey={}", ruleKey);
        return false;
      }

      boolean inUse = Boolean.TRUE.equals(rule.getEnabled())
          && rule.getStatus() == RuleStatus.ACTIVE;

      if (inUse) {
        log.debug("规则正在使用中: ruleKey={}, status={}, enabled={}",
            ruleKey, rule.getStatus(), rule.getEnabled());
      }

      return inUse;
    } catch (Exception e) {
      log.error("检查规则使用状态失败: ruleKey={}", ruleKey, e);
      return false;
    }
  }

  /**
   * 获取依赖此规则的其他规则列表
   *
   * <p>当前实现：返回空列表。
   * Phase 6 实现依赖分析后将返回实际依赖此规则的 ruleKey 列表。
   *
   * @param ruleKey 规则Key
   * @return 依赖此规则的 ruleKey 列表，如果没有依赖则返回空列表
   */
  public List<String> getDependentRules(String ruleKey) {
    // Phase 6 将实现规则间依赖分析
    // 当前阶段规则之间没有依赖关系，返回空列表
    return Collections.emptyList();
  }

  /**
   * 获取规则详细使用信息
   *
   * <p>当前实现：返回规则基本状态信息。
   * 后续增强：
   * <ul>
   *   <li>Phase 5: 填充灰度配置信息</li>
   *   <li>Phase 6: 填充依赖规则列表</li>
   * </ul>
   *
   * @param ruleKey 规则Key
   * @return 使用信息对象
   */
  public UsageInfo getUsageInfo(String ruleKey) {
    Rule rule = ruleRepository.findByRuleKey(ruleKey).orElse(null);

    if (rule == null) {
      log.debug("规则不存在: ruleKey={}", ruleKey);
      return UsageInfo.notFound(ruleKey);
    }

    UsageInfo info = new UsageInfo();
    info.setRuleKey(ruleKey);
    info.setRuleName(rule.getRuleName());
    info.setActive(Boolean.TRUE.equals(rule.getEnabled())
        && rule.getStatus() == RuleStatus.ACTIVE);
    info.setStatus(rule.getStatus().getDescription());
    info.setEnabled(rule.getEnabled());
    info.setVersion(rule.getVersion());
    info.setCreatedBy(rule.getCreatedBy());
    info.setDependentRuleKeys(getDependentRules(ruleKey));
    // Phase 5 实现灰度发布后填充
    info.setGrayscaleConfigs(Collections.emptyList());
    info.setUsingServices(Collections.emptyList());

    return info;
  }

  /**
   * 检查规则是否可以安全删除
   *
   * @param ruleKey 规则Key
   * @return true 如果规则可以安全删除，false 否则
   */
  public boolean canSafelyDelete(String ruleKey) {
    if (isInUse(ruleKey)) {
      log.warn("规则正在使用中，不能删除: ruleKey={}", ruleKey);
      return false;
    }

    List<String> dependentRules = getDependentRules(ruleKey);
    if (!dependentRules.isEmpty()) {
      log.warn("规则存在依赖关系，不能删除: ruleKey={}, dependentRules={}",
          ruleKey, dependentRules);
      return false;
    }

    return true;
  }

  /**
   * 获取规则使用详情描述文本
   *
   * @param ruleKey 规则Key
   * @return 使用详情描述
   */
  public String getUsageDetails(String ruleKey) {
    UsageInfo info = getUsageInfo(ruleKey);

    if (!info.isExists()) {
      return "规则不存在";
    }

    StringBuilder details = new StringBuilder();
    details.append("规则Key: ").append(info.getRuleKey()).append("\n");
    details.append("规则名称: ").append(info.getRuleName()).append("\n");
    details.append("状态: ").append(info.getStatus()).append("\n");
    details.append("启用: ").append(Boolean.TRUE.equals(info.getEnabled()) ? "是" : "否")
        .append("\n");
    details.append("版本: ").append(info.getVersion()).append("\n");
    details.append("创建人: ").append(info.getCreatedBy()).append("\n");
    details.append("使用状态: ").append(info.isActive() ? "正在使用中" : "未使用")
        .append("\n");

    if (!info.getDependentRuleKeys().isEmpty()) {
      details.append("依赖规则: ").append(info.getDependentRuleKeys()).append("\n");
    }

    return details.toString();
  }

  /**
   * 规则使用信息
   */
  @Data
  public static class UsageInfo {

    /** 规则Key */
    private String ruleKey;
    /** 规则名称 */
    private String ruleName;
    /** 是否存在 */
    private boolean exists = true;
    /** 是否正在使用（ACTIVE 且启用） */
    private boolean active;
    /** 规则状态描述 */
    private String status;
    /** 是否启用 */
    private Boolean enabled;
    /** 版本号 */
    private Integer version;
    /** 创建人 */
    private String createdBy;
    /** 依赖此规则的 ruleKey 列表 */
    private List<String> dependentRuleKeys = new ArrayList<>();
    /** 灰度配置列表（Phase 5 填充） */
    private List<String> grayscaleConfigs = new ArrayList<>();
    /** 使用此规则的服务列表（后续增强） */
    private List<String> usingServices = new ArrayList<>();

    /**
     * 创建表示规则不存在的 UsageInfo
     */
    public static UsageInfo notFound(String ruleKey) {
      UsageInfo info = new UsageInfo();
      info.setRuleKey(ruleKey);
      info.setExists(false);
      info.setActive(false);
      return info;
    }
  }
}
