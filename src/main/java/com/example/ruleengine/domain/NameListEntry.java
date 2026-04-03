package com.example.ruleengine.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 黑名单/白名单条目
 */
@Entity
@Table(name = "name_list")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NameListEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "list_type", nullable = false, length = 10)
    private String listType;

    @Column(name = "key_type", nullable = false, length = 20)
    private String keyType;

    @Column(name = "key_value", nullable = false, length = 256)
    private String keyValue;

    @Column(name = "list_key", nullable = false, length = 255)
    private String listKey;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 255)
    private String source;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
