package com.example.ruleengine.repository;

import com.example.ruleengine.domain.FeatureDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinition, Long> {

    Optional<FeatureDefinition> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT f FROM FeatureDefinition f WHERE " +
           "(:keyword IS NULL OR LOWER(f.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:scope IS NULL OR f.scope = :scope) AND " +
           "(:dataType IS NULL OR f.dataType = :dataType) AND " +
           "(:sourceType IS NULL OR f.sourceType = :sourceType) AND " +
           "(:sensitivity IS NULL OR f.sensitivity = :sensitivity) AND " +
           "(:status IS NULL OR f.status = :status)")
    Page<FeatureDefinition> search(
            @Param("keyword") String keyword,
            @Param("scope") String scope,
            @Param("dataType") String dataType,
            @Param("sourceType") String sourceType,
            @Param("sensitivity") String sensitivity,
            @Param("status") String status,
            Pageable pageable
    );

    List<FeatureDefinition> findByCodeIn(List<String> codes);
}
