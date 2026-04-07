package com.example.ruleengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 功能开关配置
 * 通过 application.yml 中 rule-engine.features.* 控制
 */
@Component
@ConfigurationProperties(prefix = "rule-engine.features")
@Data
public class FeatureProperties {

    private FeatureToggle multiEnvironment = new FeatureToggle();
    private FeatureToggle importExport = new FeatureToggle();

    @Data
    public static class FeatureToggle {
        private boolean enabled = false;
    }
}
