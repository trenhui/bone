package com.bone.integration;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableSqlRepositories(basePackages = "com.bone.integration.domain.repository")
public class IntegrationApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntegrationApplication.class, args);
  }
}
