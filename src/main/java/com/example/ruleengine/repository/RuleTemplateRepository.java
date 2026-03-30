package com.example.ruleengine.repository;

import com.example.ruleengine.domain.RuleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 系统预置规则模板数据访问接口
 */
@Repository
public interface RuleTemplateRepository extends JpaRepository<RuleTemplate, Long> {

    List<RuleTemplate> findByCategory(String category);

    List<RuleTemplate> findByIsSystem(Boolean isSystem);

    List<RuleTemplate> findByCategoryAndIsSystem(String category, Boolean isSystem);
}
