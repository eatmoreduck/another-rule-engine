package com.example.ruleengine.repository;

import com.example.ruleengine.domain.CustomTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户自定义规则模板数据访问接口
 */
@Repository
public interface CustomTemplateRepository extends JpaRepository<CustomTemplate, Long> {

    List<CustomTemplate> findByCreatedBy(String createdBy);
}
