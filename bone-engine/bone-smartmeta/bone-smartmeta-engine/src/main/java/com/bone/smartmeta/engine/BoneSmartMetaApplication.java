package com.bone.smartmeta.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
// 修复Spring Data JPA相关包找不到的问题
// import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bone SmartMeta 应用程序入口类
 */
@SpringBootApplication(scanBasePackages = "com.bone")
// 修复EnableJpaAuditing注解找不到的问题
// @EnableJpaAuditing
@EnableCaching
@EnableAsync
public class BoneSmartMetaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoneSmartMetaApplication.class, args);
    }
}
