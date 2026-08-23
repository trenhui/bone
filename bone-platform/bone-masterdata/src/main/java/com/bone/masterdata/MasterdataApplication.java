package com.bone.masterdata;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.masterdata.domain.repository")
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class MasterdataApplication {
  public static void main(String[] args) {
    SpringApplication.run(MasterdataApplication.class, args);
  }
}
