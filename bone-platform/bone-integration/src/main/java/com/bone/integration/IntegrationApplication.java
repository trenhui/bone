package com.bone.integration;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.bone.integration", "com.bone.core.security.jwt"})
@EnableScheduling
@EnableSqlRepositories(basePackages = "com.bone.integration.domain.repository")
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class IntegrationApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntegrationApplication.class, args);
  }
}
