package com.bone.file;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
    scanBasePackages = {
      "com.bone.file",
      "com.bone.core.security.jwt",
      "com.bone.metadata.sdk.extension.repository"
    })
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
@EnableSqlRepositories(basePackages = "com.bone.file.domain.repository")
public class FileApplication {
  public static void main(String[] args) {
    SpringApplication.run(FileApplication.class, args);
  }
}
