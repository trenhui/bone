package com.bone.procurement;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 采购应用启动壳：只做装配，业务代码在 bone-procurement-transaction。
 *
 * <p>装配口径对齐 ClaimApplication：@EnableSqlRepositories 生成 metadata-sdk 仓储实现， @Import 元数据与 SQL 仓储自动配置。
 */
@SpringBootApplication(scanBasePackages = {"com.bone.procurement", "com.bone.core.security.jwt"})
@EnableSqlRepositories(
    basePackages = {
      "com.bone.procurement.transaction.domain.repository",
      "com.bone.procurement.fulfillment.domain.repository"
    })
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class ProcurementApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProcurementApplication.class, args);
  }
}
