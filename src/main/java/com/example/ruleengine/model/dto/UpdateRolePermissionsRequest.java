package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新角色权限请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRolePermissionsRequest {

    private List<Long> permissionIds;
}
