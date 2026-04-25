package com.bone.system;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bone系统管理服务主类
 */
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.system.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.system.domain.service")
public class SystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}

