package com.bone.iam.infrastructure.debug;

import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 临时调试工具：检查数据库中的数据 - 仅在 dev 环境运行 */
@Component
@Slf4j
@Profile("dev")
public class DatabaseDebugTool implements CommandLineRunner {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Override
  public void run(String... args) {
    log.info("=== 数据库调试信息 ===");

    // 检查 MetadataSdkContext
    try {
      log.info("MetadataSdkContext initialized, appCode: {}", MetadataSdkContext.getAppCode());
    } catch (Exception e) {
      log.info("MetadataSdkContext not initialized: {}", e.getMessage());
    }

    // 检查 iam_account_role 表
    try {
      var results = jdbcTemplate.queryForList("SELECT * FROM iam_account_role");
      log.info("iam_account_role 表数据: {}", results);
    } catch (Exception e) {
      log.error("查询 iam_account_role 失败: {}", e.getMessage());
    }

    // 检查 iam_role 表
    try {
      var results = jdbcTemplate.queryForList("SELECT * FROM iam_role");
      log.info("iam_role 表数据: {}", results);
    } catch (Exception e) {
      log.error("查询 iam_role 失败: {}", e.getMessage());
    }

    log.info("=== 数据库调试结束 ===");
  }
}
