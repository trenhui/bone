package com.bone.integration;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.integration.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.integration.domain.service")
public class IntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
