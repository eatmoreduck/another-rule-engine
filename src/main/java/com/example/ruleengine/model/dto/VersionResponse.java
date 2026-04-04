package com.example.ruleengine.model.dto;

import com.example.ruleengine.constants.VersionStatus;
import com.example.ruleengine.domain.RuleVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 版本响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionResponse {

    private Long id;
    private Long ruleId;
    private String ruleKey;
    private Integer version;
    private String groovyScript;
    private String changeReason;
    private String changedBy;
    private LocalDateTime changedAt;
    private Boolean isRollback;
    private Integer rollbackFromVersion;
    private VersionStatus status;

    /**
     * 从实体转换为响应对象
     */
    public static VersionResponse fromEntity(RuleVersion entity) {
        return VersionResponse.builder()
                .id(entity.getId())
                .ruleId(entity.getRuleId())
                .ruleKey(entity.getRuleKey())
                .version(entity.getVersion())
                .groovyScript(entity.getGroovyScript())
                .changeReason(entity.getChangeReason())
                .changedBy(entity.getChangedBy())
                .changedAt(entity.getChangedAt())
                .isRollback(entity.getIsRollback())
                .rollbackFromVersion(entity.getRollbackFromVersion())
                .status(entity.getStatus())
                .build();
    }
}
