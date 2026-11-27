package com.bone.procurement.business;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 四类持久聚合的 DDL ↔ @Table/@Column 对账守卫，防止 SDK 漏列在运行期才暴露。 */
class BusinessEntityDdlCoverageTest {

  private static final Map<String, String> TABLE_COLUMNS =
      Map.of(
          "t_proc_bid",
          "tenant_id,bid_id,rfx_no,supplier_id,source_ref,due_at,status",
          "t_proc_quotation",
          "tenant_id,quote_id,rfx_no,supplier_id,amount,currency,valid_until,ai_score,ai_recommendation",
          "t_proc_award",
          "tenant_id,award_id,rfx_no,supplier_id,confirmed_by,confirmed_at,reason",
          "t_proc_capa",
          "tenant_id,capa_no,supplier_id,issue_description,owner_id,due_at,status,follow_up_required,closed_by,closed_at,evidence_ref");

  private static final Pattern CREATE_TABLE =
      Pattern.compile(
          "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?\\s*\\((.*?)\\)\\s*ENGINE=",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  @Test
  @DisplayName("四张业务表在 bone-init.sql 中存在且必备列与唯一键齐全")
  void ddlHasTablesColumnsAndUniqueKeys() throws IOException {
    String sql = Files.readString(locateInit());
    Map<String, String> blocks = new java.util.LinkedHashMap<>();
    Matcher matcher = CREATE_TABLE.matcher(sql);
    while (matcher.find()) {
      blocks.put(matcher.group(1), matcher.group(2));
    }

    for (Map.Entry<String, String> entry : TABLE_COLUMNS.entrySet()) {
      String table = entry.getKey();
      String block = blocks.get(table);
      assertThat(block).as("缺少建表：" + table).isNotNull();
      for (String column : entry.getValue().split(",")) {
        assertThat(block).as(table + " 缺列 " + column).contains("`" + column + "`");
      }
      for (String column :
          new String[] {
            "id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"
          }) {
        assertThat(block).as(table + " 缺审计列 " + column).contains("`" + column + "`");
      }
      assertThat(block)
          .as(table + " 缺租户唯一键")
          .containsPattern(
              Pattern.compile("UNIQUE\\s+KEY[^\\n]*tenant_id", Pattern.CASE_INSENSITIVE));
    }
  }

  @Test
  @DisplayName("四个 SDK 实体都声明对应 @Table 与全部业务列")
  void entitiesDeclareTableAndColumns() throws IOException {
    Path root = Path.of("src/main/java/com/bone/procurement/business/infrastructure");
    String sources;
    try (var paths = Files.walk(root)) {
      sources =
          paths
              .filter(p -> p.toString().endsWith("Entity.java"))
              .map(this::read)
              .collect(Collectors.joining("\n"));
    }
    assertThat(sources).contains("@Table(\"t_proc_bid\")");
    assertThat(sources).contains("@Table(\"t_proc_quotation\")");
    assertThat(sources).contains("@Table(\"t_proc_award\")");
    assertThat(sources).contains("@Table(\"t_proc_capa\")");
    for (String columns : TABLE_COLUMNS.values()) {
      for (String column : columns.split(",")) {
        assertThat(sources)
            .as("实体漏 @Column " + column)
            .contains("@Column(name = \"" + column + "\")");
      }
    }
  }

  private String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path locateInit() throws IOException {
    Path dir = Path.of("").toAbsolutePath();
    for (int i = 0; i < 8 && dir != null; i++) {
      Path candidate = dir.resolve("bone-init.sql");
      if (Files.exists(candidate)) {
        return candidate;
      }
      dir = dir.getParent();
    }
    throw new IOException("bone-init.sql 未找到");
  }
}
