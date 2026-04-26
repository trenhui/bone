package com.pkh.cloud.auth.sdk.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import cn.hutool.extra.spring.SpringUtil;
import com.pkh.cloud.auth.sdk.constant.AuthRedisConstant;
import com.pkh.cloud.auth.sdk.core.feign.PermissionFeign;
import com.pkh.cloud.auth.sdk.core.filter.TokenConfigure;
import com.pkh.cloud.auth.sdk.core.service.BonePermissionService;
import com.pkh.cloud.auth.sdk.util.UserUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 把controller类手动注册到 spring 容器，在启动类使用@EnableController开启
 */
@AutoConfiguration
@EnableFeignClients("com.pkh.cloud.auth.sdk.core.feign")
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
