package com.bone.iam.adapter.web.dto.resp;

import lombok.Data;

@Data
public class LoginResp {
    private String token;
    private UserInfo user;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
    }
}