package com.bone.integration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author renhui.trh
 */
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan(basePackages = {"com.bone.lowcode.integration.infrastructure.repository"})
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@SpringBootApplication(exclude = {
        SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class
})
public class BoneIntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoneIntegrationApplication.class, args);
    }
}