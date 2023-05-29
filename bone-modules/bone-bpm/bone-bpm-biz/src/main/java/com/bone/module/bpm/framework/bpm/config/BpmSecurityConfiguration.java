package com.bone.module.bpm.framework.bpm.config;

import com.bone.base.security.config.AuthorizeHttpRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

/**
 * @author kemengkai
 * @create 2022-05-07 08:15
 */
@Configuration(proxyBeanMethods = false, value = "bpmSecurityConfiguration")
public class BpmSecurityConfiguration {

    @Bean("bpmAuthorizeRequestsCustomizer")
    public AuthorizeHttpRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeHttpRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // 任务回退接口
                registry.requestMatchers(buildAdminApi("/bpm/task/back")).permitAll();
            }

        };
    }
}
