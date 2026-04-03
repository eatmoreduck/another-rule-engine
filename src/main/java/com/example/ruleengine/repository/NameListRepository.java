package com.example.ruleengine.repository;

import com.example.ruleengine.domain.NameListEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NameListRepository extends JpaRepository<NameListEntry, Long> {

    /** Hot-path: check if value exists in list within a scope (filtering expired entries) */
    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN true ELSE false END FROM NameListEntry n " +
           "WHERE n.listKey = :listKey AND n.listType = :listType " +
           "AND n.keyType = :keyType AND n.keyValue = :keyValue " +
           "AND (n.expiredAt IS NULL OR n.expiredAt > CURRENT_TIMESTAMP)")
    boolean existsActiveEntry(@Param("listKey") String listKey,
                              @Param("listType") String listType,
                              @Param("keyType") String keyType,
                              @Param("keyValue") String keyValue);

    Page<NameListEntry> findByListKeyAndListType(String listKey, String listType, Pageable pageable);

    Page<NameListEntry> findByListKeyAndListTypeAndKeyType(String listKey, String listType, String keyType, Pageable pageable);

    Page<NameListEntry> findByListKey(String listKey, Pageable pageable);

    long countByListKeyAndListType(String listKey, String listType);

    /** 获取所有不重复的 listKey */
    @Query("SELECT DISTINCT n.listKey FROM NameListEntry n ORDER BY n.listKey")
    List<String> findDistinctListKeys();
}
