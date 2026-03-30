package com.example.ruleengine.repository;

import com.example.ruleengine.constants.EnvironmentType;
import com.example.ruleengine.domain.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 环境数据访问接口
 */
@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    Optional<Environment> findByName(String name);

    List<Environment> findByType(EnvironmentType type);

    boolean existsByName(String name);
}
