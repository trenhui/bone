package com.bone.iam.infrastructure.client;

import com.bone.iam.domain.client.SsoClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class SsoClientImpl implements SsoClient {

    @Override
    public boolean authenticate(String username, String password) {
        return false;
    }

    @Override
    public String getRedirectUrl() {
        return "";
    }

    @Override
    public String processCallback(String code) {
        return "";
    }
}
