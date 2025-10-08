package com.bone.smartmeta.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bone SmartMeta 应用程序入口类
 */
@SpringBootApplication(scanBasePackages = "com.bone")
@EnableJpaAuditing
@EnableCaching
@EnableAsync
public class BoneSmartMetaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoneSmartMetaApplication.class, args);
    }
}
