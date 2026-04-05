package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDTO {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String resourceType;
    private String resourcePath;
    private String method;
    private Long parentId;
    private Integer sortOrder;
}
