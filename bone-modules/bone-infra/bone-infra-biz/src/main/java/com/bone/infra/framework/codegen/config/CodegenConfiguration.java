package com.bone.infra.framework.codegen.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration(proxyBeanMethods = false)
@Configuration
@EnableConfigurationProperties(CodegenProperties.class)
public class CodegenConfiguration {

    @Bean("codegenProperties")
    //@ConfigurationProperties(prefix = "bone.codegen")
    public CodegenProperties codegenProperties() {
        return new CodegenProperties();
    }
}
