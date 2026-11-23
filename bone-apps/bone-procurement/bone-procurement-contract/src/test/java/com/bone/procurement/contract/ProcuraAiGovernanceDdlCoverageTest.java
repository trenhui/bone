package com.bone.procurement.contract;

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
 * procuraAI 生产强化：采购域 DDL ↔ 实体对账守卫。
 *
 * <p>本测试位于 contract 模块，但通过文件路径读取 contract + ai-governance 两个模块的实体源码， 断言 bone-init.sql 共 24 张
 * t_proc_* 表、9 张新表（合同/治理/事件闭环域）必备列齐全、审查表幂等双唯一键、 全部新表唯一键以 tenant_id 打头。不执行真实 MySQL。
 */
class ProcuraAiGovernanceDdlCoverageTest {

  private static final List<String> NEW_TABLES =
      List.of(
          "t_proc_contract_review",
          "t_proc_review_trace",
          "t_proc_ai_capability",
          "t_proc_human_decision",
          "t_proc_global_kill_switch",
          "t_proc_governance_trace",
          "t_proc_contract_outbox",
          "t_proc_contract_inbox",
          "t_proc_contract_dlq");

  private static final List<String> REQUIRED_COLUMNS =
      List.of(
          "id",
          "tenant_id",
          "created_by",
          "updated_by",
          "created_at",
          "updated_at",
          "deleted",
          "version");

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern UNIQUE_KEY =
      Pattern.compile("UNIQUE KEY\\s+`([^`]+)`\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
  private static final Pattern TABLE_ANNOTATION =
      Pattern.compile("@Table\\s*\\(\\s*\"([A-Za-z0-9_]+)\"\\s*\\)");

  @Test
  @DisplayName("bone-init.sql 共 24 张 t_proc_* 表，9 张新表均已建表")
  void twentyFourTablesDeclared() throws IOException {
    Map<String, String> ddl = readInitTables();
    List<String> all = ddl.keySet().stream().filter(t -> t.startsWith("t_proc_")).toList();

    assertThat(all).as("t_proc_* 表数应为 24").hasSize(24);
    for (String table : NEW_TABLES) {
      assertThat(ddl).as("缺少建表：" + table).containsKey(table);
    }
  }

  @Test
  @DisplayName("9 张新表审计八列齐全")
  void newTablesHaveRequiredColumns() throws IOException {
    Map<String, String> ddl = readInitTables();
    List<String> offenders = new ArrayList<>();
    for (String table : NEW_TABLES) {
      String body = ddl.get(table);
      if (body == null) {
        offenders.add(table + ": 无建表");
        continue;
      }
      for (String col : REQUIRED_COLUMNS) {
        if (!Pattern.compile("^\\s*`?" + col + "`?\\s", Pattern.MULTILINE).matcher(body).find()) {
          offenders.add(table + ": 缺列 " + col);
        }
      }
    }
    assertThat(offenders).isEmpty();
  }

  @Test
  @DisplayName("审查表有 review_id/idempotency_key 且两条唯一键均以 tenant_id 打头")
  void reviewTableHasIdempotencyUniqueKeys() throws IOException {
    Map<String, String> ddl = readInitTables();
    String body = ddl.get("t_proc_contract_review");
    assertThat(body).isNotNull();
    assertThat(body).contains("`review_id`").contains("`idempotency_key`");

    Map<String, String> uks = uniqueKeys(body);
    assertThat(uks).as("审查表应有 2 条唯一键").hasSize(2);
    assertThat(uks.values()).allSatisfy(cols -> assertThat(cols.trim()).startsWith("`tenant_id`"));
    assertThat(uks.values()).anySatisfy(cols -> assertThat(cols).contains("`review_id`"));
    assertThat(uks.values()).anySatisfy(cols -> assertThat(cols).contains("`idempotency_key`"));
  }

  @Test
  @DisplayName("9 张新表的全部唯一键均以 tenant_id 打头")
  void everyNewTableUniqueKeyStartsWithTenant() throws IOException {
    Map<String, String> ddl = readInitTables();
    for (String table : NEW_TABLES) {
      Map<String, String> uks = uniqueKeys(ddl.get(table));
      assertThat(uks).as(table + " 至少一条唯一键").isNotEmpty();
      assertThat(uks.values())
          .as(table + " 唯一键必须以 tenant_id 打头")
          .allSatisfy(cols -> assertThat(cols.trim()).startsWith("`tenant_id`"));
    }
  }

  @Test
  @DisplayName("两个模块均声明了 9 个 @Table 实体，与 DDL 一一对应")
  void entitiesDeclaredForAllNewTables() throws IOException {
    List<String> entities = new ArrayList<>();
    Path root = locateRoot();
    for (String module : List.of("bone-procurement-contract", "bone-procurement-ai-governance")) {
      Path src =
          root.resolve("bone-apps/bone-procurement").resolve(module).resolve("src/main/java");
      try (Stream<Path> paths = Files.walk(src)) {
        for (Path p : paths.filter(x -> x.toString().endsWith(".java")).toList()) {
          Matcher m = TABLE_ANNOTATION.matcher(Files.readString(p));
          if (m.find()) entities.add(m.group(1));
        }
      }
    }
    assertThat(entities).as("9 个新实体都应有 @Table").containsAll(NEW_TABLES);
  }

  private static Map<String, String> uniqueKeys(String body) {
    Map<String, String> out = new LinkedHashMap<>();
    Matcher m = UNIQUE_KEY.matcher(body);
    while (m.find()) out.put(m.group(1), m.group(2));
    return out;
  }

  private static Map<String, String> readInitTables() throws IOException {
    String sql = Files.readString(locateRoot().resolve("bone-init.sql"));
    Map<String, String> out = new LinkedHashMap<>();
    Matcher m = CREATE_TABLE.matcher(sql);
    while (m.find()) out.put(m.group(1), m.group(2));
    return out;
  }

  private static Path locateRoot() throws IOException {
    Path dir = Path.of("").toAbsolutePath();
    for (int i = 0; i < 8 && dir != null; i++) {
      if (Files.exists(dir.resolve("bone-init.sql"))) return dir;
      dir = dir.getParent();
    }
    throw new IOException("bone-init.sql 未找到");
  }
}
