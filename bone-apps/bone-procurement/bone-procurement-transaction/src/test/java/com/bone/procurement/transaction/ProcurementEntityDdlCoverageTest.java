package com.bone.procurement.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 采购交易 DDL ↔ 实体对账守卫。
 *
 * <p>HC-006：SDK 持久化完全由 @Table/@Column 驱动，漏声明在运行期才 500。本测试把「建表缺列」与
 * 「实体漏 @Column」两类延迟暴露缺陷提前到构建期：每个 @Table 实体必须有同名表，且 DDL 的每列都有 对应字段（含继承的审计列），声明 @Version 的实体其表必须有
 * version 列。
 */
class ProcurementEntityDdlCoverageTest {

  private static final List<String> EXPECTED_TABLES =
      List.of("t_proc_requisition", "t_proc_po", "t_proc_po_line", "t_proc_price_snapshot");

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\s*\\(\\s*\"([A-Za-z0-9_]+)\"\\s*\\)");

  @Test
  @DisplayName("4 张 t_proc_* 表在 bone-init.sql 都有建表语句")
  void allProcurementTablesDeclared() throws IOException {
    Map<String, String> ddl = readInitTables();
    for (String table : EXPECTED_TABLES) {
      assertThat(ddl).as("缺少建表：" + table).containsKey(table);
    }
  }

  @Test
  @DisplayName("每个 @Table 实体都有对应建表，且 DDL 必备列齐全")
  void entitiesHaveMatchingTablesAndRequiredColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    Map<String, String> entities = readEntities();
    assertThat(entities).as("没有任何 @Table 实体——解析失效会让门禁恒过").isNotEmpty();

    List<String> offenders = new ArrayList<>();
    for (Map.Entry<String, String> e : entities.entrySet()) {
      String table = e.getKey();
      String body = ddl.get(table);
      if (body == null) {
        offenders.add(table + ": 无建表");
        continue;
      }
      for (String required :
          List.of(
              "id",
              "tenant_id",
              "created_at",
              "updated_at",
              "created_by",
              "updated_by",
              "deleted",
              "version")) {
        if (!Pattern.compile("^\\s*`?" + required + "`?\\s", Pattern.MULTILINE)
            .matcher(body)
            .find()) {
          offenders.add(table + ": 缺列 " + required);
        }
      }
    }
    assertThat(offenders).isEmpty();
  }

  @Test
  @DisplayName("金额列为 DECIMAL(19,4)，数量列为 DECIMAL(19,6)，币种 CHAR(3)")
  void moneyPrecisionColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    assertThat(ddl.get("t_proc_po_line")).contains("`net_price`           DECIMAL(19,4)");
    assertThat(ddl.get("t_proc_po_line")).contains("`quantity`            DECIMAL(19,6)");
    assertThat(ddl.get("t_proc_po")).contains("`currency`            CHAR(3)");
  }

  private Map<String, String> readInitTables() throws IOException {
    Path init = locateInit();
    String sql = Files.readString(init);
    Map<String, String> out = new LinkedHashMap<>();
    Matcher m = CREATE_TABLE.matcher(sql);
    while (m.find()) {
      out.put(m.group(1), m.group(2));
    }
    return out;
  }

  private Map<String, String> readEntities() throws IOException {
    Path root = Path.of("src/main/java");
    Map<String, String> out = new LinkedHashMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path p : paths.filter(x -> x.toString().endsWith(".java")).toList()) {
        String src = Files.readString(p);
        Matcher m = TABLE_ANNOTATION.matcher(src);
        if (m.find()) {
          out.put(m.group(1), src);
        }
      }
    }
    return out;
  }

  private Path locateInit() throws IOException {
    Path dir = Path.of("").toAbsolutePath();
    for (int i = 0; i < 8 && dir != null; i++) {
      Path candidate = dir.resolve("bone-init.sql");
      if (Files.exists(candidate)) return candidate;
      dir = dir.getParent();
    }
    throw new IOException("bone-init.sql 未找到");
  }
}
