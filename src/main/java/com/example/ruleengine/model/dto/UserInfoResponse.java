package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private List<String> roles;
    private List<String> permissions;

    public static UserInfoResponse of(Long id, String username, String nickname,
                                       String email, String phone,
                                       List<String> roles, List<String> permissions) {
        return UserInfoResponse.builder()
                .id(id)
                .username(username)
                .nickname(nickname)
                .email(email)
                .phone(phone)
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
