package com.pkh.cloud.auth.sdk.constant;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
public class AuthRedisConstant {
    @Value("${sa-token.token-name:Satoken}")
     String tokenName;
    @Value("${sa-token.timeout:28800}")
    public Long timeout;
    public String getUserRoleKey(String userCode){
        return tokenName + ":" + "role" + ":" + userCode;
    }

    public String getUserPermissionKey(String userCode){
        return tokenName + ":" + "permission" + ":" + userCode;
    }

    public String getUserRoleLikeKey(){
        return tokenName + ":" + "role" + ":" + "*";
    }

    public String getUserPermissionLikeKey(){
        return tokenName + ":" + "permission" + ":" + "*";
    }

}
