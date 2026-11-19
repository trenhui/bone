package com.bone.procurement.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 权限码 / 菜单种子 SQL 静态契约（本地无 MySQL 实例，无法执行迁移，故做静态校验并如实登记）。
 *
 * <p>校验：G40 权限码命名、幂等（NOT EXISTS）、绑超管、号段与既有采购迁移（0099/0100）不冲突、菜单校验过程防假成功。
 */
class ProcurementContractReviewMigrationTest {

  private static final String PERMISSION_SQL =
      "0127_seed_procurement_contract_review_permissions.sql";
  private static final String MENU_SQL = "0128_seed_procurement_contract_review_menu.sql";

  @Test
  void 权限码符合G40命名且幂等绑超管() throws IOException {
    String sql = read(PERMISSION_SQL);

    assertThat(sql).contains("'procurement:contract:review:read'");
    assertThat(sql).contains("'procurement:contract:review:write'");
    for (String code :
        new String[] {"procurement:contract:review:read", "procurement:contract:review:write"}) {
      assertThat(code.split(":")).as("G40：域:资源:子资源:动作 四段命名").hasSize(4);
    }
    assertThat(countOf(sql, "NOT EXISTS")).as("权限与绑定均需幂等").isGreaterThanOrEqualTo(2);
    assertThat(sql).contains("1 AS role_id").as("绑定超管 role 1");
    assertThat(sql).contains("iam_permission").contains("iam_role_permission");
  }

  @Test
  void 菜单种子幂等且挂采购目录校验() throws IOException {
    String sql = read(MENU_SQL);

    assertThat(sql).contains("'/procurement#/contract-review'");
    assertThat(sql).contains("'procurement:contract:review:read'");
    assertThat(countOf(sql, "NOT EXISTS")).as("菜单插入需幂等").isGreaterThanOrEqualTo(1);
    assertThat(sql).contains("SIGNAL SQLSTATE").as("防插 0 条但 EXIT 0 假成功");
    assertThat(sql).contains("parent_id").as("挂父目录");
    assertThat(sql).contains("ShoppingOutlined").as("白名单图标");
  }

  @Test
  void 新号段与既有采购迁移不冲突() throws IOException {
    String permissions = read(PERMISSION_SQL);
    String menu = read(MENU_SQL);
    String old0099 = read("0099_seed_procurement_permissions.sql");
    String old0100 = read("0100_seed_procurement_menus.sql");

    assertThat(permissions).contains("930000000000000001").contains("930000000000000002");
    assertThat(menu).contains("930000000000000200");
    // 新段 93... 不复用 0099（920...001/101）与 0100（920...200）。
    assertThat(permissions)
        .doesNotContain("920000000000000001")
        .doesNotContain("920000000000000101");
    assertThat(menu).doesNotContain("920000000000000200");
    assertThat(old0099).contains("920000000000000001");
    assertThat(old0100).contains("920000000000000200");
  }

  private static String read(String name) throws IOException {
    Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null && !Files.exists(dir.resolve("scripts").resolve("migration"))) {
      dir = dir.getParent();
    }
    assertThat(dir).as("未找到仓库根目录（scripts/migration）").isNotNull();
    return Files.readString(
        dir.resolve("scripts").resolve("migration").resolve(name), StandardCharsets.UTF_8);
  }

  private static int countOf(String text, String token) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(token, idx)) >= 0) {
      count++;
      idx += token.length();
    }
    return count;
  }
}
