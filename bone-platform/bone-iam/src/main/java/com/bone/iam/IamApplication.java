package com.bone.iam;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.bone.iam")
@EnableSqlRepositories(basePackages = "com.bone.iam.domain.repository")
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class IamApplication {
  public static void main(String[] args) {
    SpringApplication.run(IamApplication.class, args);
  }
}
