package com.bone.metadata.auth.sdk.core.filter;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Slf4j
public class TokenConfigure implements WebMvcConfigurer {
    @Value("${sa-token.disable:false}")
    Boolean filterFlag;
    @Value("${sa-token.excludePath:}")
    List<String> excludePathPatterns;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("excludePathPatterns:{}",excludePathPatterns);
        if(!filterFlag) {
            registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                    .addPathPatterns("/**")
                    .excludePathPatterns("/system/auth/**")
                    .excludePathPatterns(excludePathPatterns)
            ;
        }
    }
}
