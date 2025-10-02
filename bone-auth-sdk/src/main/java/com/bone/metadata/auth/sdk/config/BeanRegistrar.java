package com.bone.metadata.auth.sdk.config;

import com.bone.metadata.auth.sdk.constant.AuthRedisConstant;
import com.bone.metadata.auth.sdk.core.filter.TokenConfigure;
import com.bone.metadata.auth.sdk.core.service.BonePermissionService;
import com.bone.metadata.auth.sdk.util.UserUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 把controller类手动注册到 spring 容器，在启动类使用@EnableController开启
 */
@AutoConfiguration
@EnableFeignClients("com.bone.cloud.auth.sdk.core.feign")
public class BeanRegistrar {
//    @Bean
//    public SsoClientController getSsoClientController() {
//        return new SsoClientController();
//    }

    @Bean
    public TokenConfigure getTokenConfigure(){
        return new TokenConfigure();
    }
    @Bean
    public AuthRedisConstant getAuthRedisConstant(){
        return new AuthRedisConstant();
    }

    @Bean
    public BonePermissionService getBonePermissionService(){
        return new BonePermissionService();
    }

    @Bean
    public UserUtil getUserUtil(){
        return new UserUtil();
    }

//    @Bean
//    public SsoConfig getSsoConfig() {
//        return new SsoConfig();
//    }
}
