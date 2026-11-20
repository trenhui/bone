package com.bone.procurement.risk;

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

/** 风险绩效 DDL ↔ 实体对账守卫：2 张表存在、审计列齐全。 */
class RiskDdlCoverageTest {

  private static final List<String> EXPECTED_TABLES =
      List.of("t_proc_risk_alert", "t_proc_performance_score");

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\s*\\(\\s*\"([A-Za-z0-9_]+)\"\\s*\\)");

  @Test
  @DisplayName("2 张风险绩效表都有建表语句")
  void allTablesDeclared() throws IOException {
    Map<String, String> ddl = readInitTables();
    for (String table : EXPECTED_TABLES) {
      assertThat(ddl).as("缺少建表：" + table).containsKey(table);
    }
  }

  @Test
  @DisplayName("每个 @Table 实体都有对应表且审计列齐全")
  void entitiesHaveMatchingTablesAndRequiredColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    Map<String, String> entities = readEntities();
    assertThat(entities).isNotEmpty();
    List<String> offenders = new ArrayList<>();
    for (String table : entities.keySet()) {
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
  @DisplayName("预警来源幂等键存在")
  void sourceDedupKeyExists() throws IOException {
    Map<String, String> ddl = readInitTables();
    assertThat(ddl.get("t_proc_risk_alert")).contains("uk_proc_risk_alert_dedup");
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
