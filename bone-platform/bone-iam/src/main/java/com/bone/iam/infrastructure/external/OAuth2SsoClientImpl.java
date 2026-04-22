package com.bone.iam.infrastructure.external;

import com.bone.iam.domain.client.SsoClient;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SsoClientImpl implements SsoClient {
    @Override
    public boolean authenticate(String username, String password) {
        // 实现OAuth2认证逻辑
        return false;
    }

    @Override
    public String getRedirectUrl() {
        // 实现获取OAuth2重定向URL逻辑
        return "";
    }

    @Override
    public String processCallback(String code) {
        // 实现处理OAuth2回调逻辑
        return "";
    }
}