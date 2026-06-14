package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据库修复控制器 - 用于修复初始化数据问题 */
@RestController
@RequestMapping("/api/v1/iam/debug")
@RequiredArgsConstructor
@Slf4j
public class DatabaseFixController {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  /** 重置admin密码为123456 */
  @PostMapping("/reset-admin-password")
  public ApiResponse<String> resetAdminPassword() {
    try {
      String encoded = passwordEncoder.encode("123456");
      jdbcTemplate.update(
          "UPDATE iam_account SET password_hash = ?, login_fail_count = 0, locked_at = NULL WHERE username = 'admin'",
          encoded);
      log.info("已重置admin密码, new hash={}", encoded);
      return ApiResponse.success("已重置admin密码为123456");
    } catch (Exception e) {
      log.error("重置密码失败: {}", e.getMessage(), e);
      return ApiResponse.error("RESET_FAILED", "重置密码失败: " + e.getMessage());
    }
  }

  /**
   * 完整修复 IAM 数据 1. 修复 iam_role 表的 id（将字符串 id 改为数字 id） 2. 修复 iam_account_role 表的 role_id 3. 修复
   * iam_role_permission 表的 role_id
   */
  @PostMapping("/fix-all-iam-data")
  @Transactional
  public ApiResponse<String> fixAllIamData() {
    try {
      // 1. 查看当前数据
      List<Map<String, Object>> roles = jdbcTemplate.queryForList("SELECT * FROM iam_role");
      log.info("iam_role 表数据: {}", roles);

      List<Map<String, Object>> accountRoles =
          jdbcTemplate.queryForList("SELECT * FROM iam_account_role");
      log.info("iam_account_role 表数据: {}", accountRoles);

      List<Map<String, Object>> rolePermissions =
          jdbcTemplate.queryForList("SELECT * FROM iam_role_permission");
      log.info("iam_role_permission 表数据: {}", rolePermissions);

      // 2. 查找 SUPER_ADMIN 和 USER 角色的字符串 id
      String superAdminStrId = null;
      String userStrId = null;
      for (Map<String, Object> role : roles) {
        Object code = role.get("code");
        if (code != null && code.toString().equalsIgnoreCase("SUPER_ADMIN")) {
          superAdminStrId = role.get("id").toString();
        } else if (code != null && code.toString().equalsIgnoreCase("USER")) {
          userStrId = role.get("id").toString();
        }
      }

      // 3. 查找 db_id（可能是正确的数字 id）
      Long superAdminRealId = null;
      Long userRealId = null;
      for (Map<String, Object> role : roles) {
        Object code = role.get("code");
        Object dbId = role.get("db_id");
        if (code != null
            && code.toString().equalsIgnoreCase("SUPER_ADMIN")
            && dbId instanceof Number) {
          superAdminRealId = ((Number) dbId).longValue();
        } else if (code != null
            && code.toString().equalsIgnoreCase("USER")
            && dbId instanceof Number) {
          userRealId = ((Number) dbId).longValue();
        }
      }

      // 4. 如果 db_id 不存在，使用固定值 1 和 2
      if (superAdminRealId == null) superAdminRealId = 1L;
      if (userRealId == null) userRealId = 2L;

      log.info("SUPER_ADMIN: strId={}, realId={}", superAdminStrId, superAdminRealId);
      log.info("USER: strId={}, realId={}", userStrId, userRealId);

      // 5. 修复 iam_role 表的 id（如果需要）
      if (superAdminStrId != null && !superAdminStrId.equals(String.valueOf(superAdminRealId))) {
        jdbcTemplate.update(
            "UPDATE iam_role SET id = ? WHERE id = ?", superAdminRealId, superAdminStrId);
        log.info("更新 iam_role 表: {} -> {}", superAdminStrId, superAdminRealId);
      }
      if (userStrId != null && !userStrId.equals(String.valueOf(userRealId))) {
        jdbcTemplate.update("UPDATE iam_role SET id = ? WHERE id = ?", userRealId, userStrId);
        log.info("更新 iam_role 表: {} -> {}", userStrId, userRealId);
      }

      // 6. 修复 iam_account_role 表
      if (superAdminStrId != null) {
        jdbcTemplate.update(
            "UPDATE iam_account_role SET role_id = ? WHERE role_id = ?",
            superAdminRealId,
            superAdminStrId);
        log.info("更新 iam_account_role 表的 role_id: {} -> {}", superAdminStrId, superAdminRealId);
      }

      // 7. 修复 iam_role_permission 表
      if (superAdminStrId != null) {
        jdbcTemplate.update(
            "UPDATE iam_role_permission SET role_id = ? WHERE role_id = ?",
            superAdminRealId,
            superAdminStrId);
        log.info("更新 iam_role_permission 表的 role_id: {} -> {}", superAdminStrId, superAdminRealId);
      }

      // 8. 验证修复结果
      List<Map<String, Object>> fixedRoles = jdbcTemplate.queryForList("SELECT * FROM iam_role");
      log.info("修复后 iam_role 表数据: {}", fixedRoles);

      List<Map<String, Object>> fixedAccountRoles =
          jdbcTemplate.queryForList("SELECT * FROM iam_account_role");
      log.info("修复后 iam_account_role 表数据: {}", fixedAccountRoles);

      return ApiResponse.success("成功修复 IAM 数据");

    } catch (Exception e) {
      log.error("修复失败: {}", e.getMessage(), e);
      return ApiResponse.error("FIX_FAILED", "修复失败: " + e.getMessage());
    }
  }

  /** 简单修复 - 仅修复 iam_account_role 表 */
  @PostMapping("/fix-account-role")
  public ApiResponse<String> fixAccountRole() {
    try {
      List<Map<String, Object>> accountRoles =
          jdbcTemplate.queryForList("SELECT * FROM iam_account_role");
      log.info("iam_account_role 表数据: {}", accountRoles);

      // 直接删除所有数据并重新插入
      jdbcTemplate.execute("DELETE FROM iam_account_role");

      // 查找 SUPER_ADMIN 角色的 id
      Long superAdminId = 1L; // 默认值
      List<Map<String, Object>> roles =
          jdbcTemplate.queryForList("SELECT * FROM iam_role WHERE code = 'SUPER_ADMIN'");
      if (!roles.isEmpty()) {
        Object id = roles.get(0).get("id");
        if (id instanceof Number) {
          superAdminId = ((Number) id).longValue();
        }
      }

      // 查找 admin 账户的 id
      Long adminId = 1L; // 默认值
      List<Map<String, Object>> accounts =
          jdbcTemplate.queryForList("SELECT * FROM iam_account WHERE username = 'admin'");
      if (!accounts.isEmpty()) {
        Object id = accounts.get(0).get("id");
        if (id instanceof Number) {
          adminId = ((Number) id).longValue();
        }
      }

      // 插入正确的关联关系
      jdbcTemplate.update(
          "INSERT INTO iam_account_role (id, tenant_id, account_id, role_id) VALUES (?, 0, ?, ?)",
          System.currentTimeMillis(),
          adminId,
          superAdminId);

      return ApiResponse.success("成功修复 iam_account_role 表数据");

    } catch (Exception e) {
      log.error("修复失败: {}", e.getMessage(), e);
      return ApiResponse.error("FIX_FAILED", "修复失败: " + e.getMessage());
    }
  }

  /** 创建缺失的表 */
  @PostMapping("/create-missing-tables")
  public ApiResponse<String> createMissingTables() {
    try {
      // 创建 iam_refresh_token 表
      jdbcTemplate.execute(
          """
                CREATE TABLE IF NOT EXISTS iam_refresh_token (
                    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
                    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
                    account_id          BIGINT          NOT NULL COMMENT '账户ID',
                    token_hash          VARCHAR(500)    NOT NULL COMMENT 'Token哈希',
                    expires_at          DATETIME(3)     NOT NULL COMMENT '过期时间',
                    is_revoked          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已撤销',
                    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_iam_refresh_token_hash (token_hash),
                    KEY idx_iam_refresh_token_account (account_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新Token表'
            """);
      log.info("成功创建 iam_refresh_token 表");

      return ApiResponse.success("成功创建缺失的表");

    } catch (Exception e) {
      log.error("创建表失败: {}", e.getMessage(), e);
      return ApiResponse.error("CREATE_TABLE_FAILED", "创建表失败: " + e.getMessage());
    }
  }
}
