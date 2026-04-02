package com.example.ruleengine.cache;

import com.example.ruleengine.domain.Rule;
import com.example.ruleengine.repository.RuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer {

    private final RuleRepository ruleRepository;
    private final RuleCacheService ruleCacheService;

    /**
     * 应用启动时预热缓存
     */
    @PostConstruct
    public void init() {
        log.info("开始预热缓存...");
        warmUpCache();
        log.info("缓存预热完成");
    }

    /**
     * 定时预热缓存（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledWarmUp() {
        log.debug("定时预热缓存");
        warmUpCache();
    }

    /**
     * 预热所有生效中的规则
     */
    private void warmUpCache() {
        List<Rule> activeRules = ruleRepository.findByEnabledTrue().stream()
                .filter(r -> !r.getDeleted()).toList();
        log.info("预加载 {} 条生效中的规则到缓存", activeRules.size());

        int successCount = 0;
        for (Rule rule : activeRules) {
            try {
                ruleCacheService.getEnabledRule(rule.getRuleKey());
                successCount++;
            } catch (Exception e) {
                log.warn("预热规则缓存失败: ruleKey={}", rule.getRuleKey(), e);
            }
        }

        log.info("缓存预热完成: {}/{} 成功", successCount, activeRules.size());
    }
}
