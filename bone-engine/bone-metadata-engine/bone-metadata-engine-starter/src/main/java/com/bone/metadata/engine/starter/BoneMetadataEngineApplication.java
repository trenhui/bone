package com.bone.metadata.engine.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/** Bone Metadata Engine 应用程序入口类（未启用 JPA Auditing） */
@SpringBootApplication(scanBasePackages = "com.bone")
@EnableCaching
@EnableAsync
public class BoneMetadataEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(BoneMetadataEngineApplication.class, args);
  }
}
