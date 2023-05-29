package com.netease.mis.bsm.budget.infrastructure.config;

import com.bone.core.domain.SpringSecurityAuditorAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@Configuration
@EnableJdbcAuditing
class JdbcAuditorConfig {

    @Bean
    public SpringSecurityAuditorAware auditorProvider() {
        return  new SpringSecurityAuditorAware();
    }
}