package com.bone.metadata;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author renhui.trh
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableSqlRepositories(basePackages = "com.bone.metadata.catalog.domain.repository")
public class MetadataApplication {
  public static void main(String[] args) {
    SpringApplication.run(MetadataApplication.class, args);
  }
}
