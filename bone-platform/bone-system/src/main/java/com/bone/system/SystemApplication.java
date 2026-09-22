package com.bone.system;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Bone系统管理服务主类 */
@SpringBootApplication(
    scanBasePackages = {"com.bone.system", "com.bone.core.security.jwt", "com.bone.platform.alert"})
@EnableSqlRepositories(
    basePackages = {
      "com.bone.system.domain.repository",
      "com.bone.platform.alert.domain.repository"
    })
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class SystemApplication {
  public static void main(String[] args) {
    SpringApplication.run(SystemApplication.class, args);
  }
}
