package com.example.ruleengine.repository;

import com.example.ruleengine.domain.FeatureAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureAliasRepository extends JpaRepository<FeatureAlias, Long> {

    Optional<FeatureAlias> findByAliasCodeIgnoreCase(String aliasCode);

    List<FeatureAlias> findByCanonicalCodeOrderByAliasCodeAsc(String canonicalCode);

    List<FeatureAlias> findByCanonicalCodeIn(List<String> canonicalCodes);

    boolean existsByAliasCodeIgnoreCase(String aliasCode);

    void deleteByCanonicalCode(String canonicalCode);
}
