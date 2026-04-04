package com.example.ruleengine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String username;
    private String nickname;
    private List<String> roles;

    public static LoginResponse of(String token, String username, String nickname, List<String> roles) {
        return LoginResponse.builder()
                .token(token)
                .username(username)
                .nickname(nickname)
                .roles(roles)
                .build();
    }
}
