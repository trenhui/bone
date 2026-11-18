package com.bone.procurement.fulfillment;

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

/** 履约 DDL ↔ 实体对账守卫：5 张表存在、必备列齐全、金额/数量精度符合 LLD §14。 */
class FulfillmentDdlCoverageTest {

  private static final List<String> EXPECTED_TABLES =
      List.of(
          "t_proc_po_ack",
          "t_proc_asn",
          "t_proc_asn_line",
          "t_proc_invoice_submission",
          "t_proc_ir_exception");

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\s*\\(\\s*\"([A-Za-z0-9_]+)\"\\s*\\)");

  @Test
  @DisplayName("5 张履约表都有建表语句")
  void allFulfillmentTablesDeclared() throws IOException {
    Map<String, String> ddl = readInitTables();
    for (String table : EXPECTED_TABLES) {
      assertThat(ddl).as("缺少建表：" + table).containsKey(table);
    }
  }

  @Test
  @DisplayName("每个 @Table 实体都有对应表，必备列齐全")
  void entitiesHaveMatchingTablesAndRequiredColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    Map<String, String> entities = readEntities();
    assertThat(entities).as("没有 @Table 实体——解析失效").isNotEmpty();
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
  @DisplayName("金额 DECIMAL(19,4)、数量 DECIMAL(19,6)、币种 CHAR(3)")
  void moneyPrecisionColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    assertThat(ddl.get("t_proc_invoice_submission"))
        .contains("`amount`              DECIMAL(19,4)");
    assertThat(ddl.get("t_proc_invoice_submission")).contains("`currency`            CHAR(3)");
    assertThat(ddl.get("t_proc_asn_line")).contains("`qty`                 DECIMAL(19,6)");
  }

  private Map<String, String> readInitTables() throws IOException {
    String sql = Files.readString(locateInit());
    Map<String, String> out = new LinkedHashMap<>();
    Matcher m = CREATE_TABLE.matcher(sql);
    while (m.find()) out.put(m.group(1), m.group(2));
    return out;
  }

  private Map<String, String> readEntities() throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
      for (Path p : paths.filter(x -> x.toString().endsWith(".java")).toList()) {
        Matcher m = TABLE_ANNOTATION.matcher(Files.readString(p));
        if (m.find()) out.put(m.group(1), "");
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
