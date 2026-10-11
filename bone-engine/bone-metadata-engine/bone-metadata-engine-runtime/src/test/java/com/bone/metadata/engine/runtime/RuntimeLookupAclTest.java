package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * v0.50 lookup-options 行级过滤：REFERENCE 候选列表不得泄露「他人私有单据」——目标实体有行策略（含默认
 * owner）时仅返回操作者可见候选；管理员（null）/非数值主体豁免；无 created_by 主数据不启用。
 *
 * <p>背景：子表端点（listChildren/replaceChildren）已前置主行 ACL（RuntimeRowPolicyTest 旁路用例）；本类补 REFERENCE
 * 候选旁路的最后一块——lookupOptions 内部行级过滤。
 */
class RuntimeLookupAclTest {

  private static final long TENANT_1 = 1L;

  private EmbeddedDatabase db;
  private NamedParameterJdbcTemplate jdbc;
  private RuntimeRelationSupport support;

  @BeforeEach
  void setUp() {
    db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .setName("md_lookup_acl;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
            .build();
    jdbc = new NamedParameterJdbcTemplate(db);
    // 目标表：有 created_by（行策略默认 owner 生效）+ 无策略声明
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE acl_target ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`name` VARCHAR(64),"
                + "`version` INT NOT NULL DEFAULT 0,"
                + "`deleted` SMALLINT NOT NULL DEFAULT 0,"
                + "`created_by` VARCHAR(64),"
                + "PRIMARY KEY (`id`))");
    // 源表：引用 acl_target 的字段（labelField=name）
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE acl_source ("
                + "`id` BIGINT NOT NULL,"
                + "`tenant_id` BIGINT NOT NULL,"
                + "`target_id` BIGINT,"
                + "PRIMARY KEY (`id`))");
    // 数据：1001 两条 + 9009 一条（同租户）
    jdbc.getJdbcTemplate()
        .update(
            "INSERT INTO acl_target (id, tenant_id, name, created_by) VALUES "
                + "(1, 1, '本人候选A', '1001'), (2, 1, '本人候选B', '1001'), (3, 1, '他人候选', '9009')");
    support = new RuntimeRelationSupport(jdbc, aclCatalog());
  }

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
  }

  private RuntimeEntityCatalog aclCatalog() {
    return (code, tenantId) -> {
      if ("acl_target".equals(code)) {
        return Optional.of(
            new PublishedRuntimeEntity(
                "acl_target",
                "acl_target",
                "id",
                tenantId,
                List.of(
                    new RuntimeFieldColumn("id", "LONG", true, false, true),
                    new RuntimeFieldColumn("name", "STRING", true, false, false)),
                null,
                null)); // 无 row_policies → 默认 owner_field=created_by
      }
      if ("acl_source".equals(code)) {
        return Optional.of(
            new PublishedRuntimeEntity(
                "acl_source",
                "acl_source",
                "id",
                tenantId,
                List.of(
                    new RuntimeFieldColumn("id", "LONG", true, false, true),
                    new RuntimeFieldColumn(
                        "target_id",
                        RuntimeRelationSpec.TYPE_REFERENCE,
                        false,
                        false,
                        false,
                        null,
                        null,
                        "{\"reference\":{\"targetEntityCode\":\"acl_target\",\"labelField\":\"name\"}}"))));
      }
      return Optional.empty();
    };
  }

  @Test
  @DisplayName("lookup 过滤：本人候选全量返回，他人候选被滤掉")
  void lookup_filtersOtherOwnersRows() {
    RuntimeFieldColumn field =
        aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow().columns().get(1);
    List<Map<String, Object>> rows =
        support.lookupOptions(
            aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow(),
            field,
            TENANT_1,
            null,
            50,
            "1001");
    assertEquals(2, rows.size(), "本人候选应全量返回");
    assertTrue(rows.stream().noneMatch(r -> "他人候选".equals(r.get("name"))), "他人候选不得泄露");
  }

  @Test
  @DisplayName("lookup 豁免：管理员（null）返回全量候选")
  void lookup_adminSeesAll() {
    RuntimeFieldColumn field =
        aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow().columns().get(1);
    List<Map<String, Object>> rows =
        support.lookupOptions(
            aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow(),
            field,
            TENANT_1,
            null,
            50,
            null);
    assertEquals(3, rows.size(), "管理员候选全量");
  }

  @Test
  @DisplayName("lookup 过滤：陌生人候选为空（不泄露他人候选）")
  void lookup_strangerSeesNothing() {
    RuntimeFieldColumn field =
        aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow().columns().get(1);
    // 9999 在数据中不存在 → 按默认 owner 过滤后应无候选
    List<Map<String, Object>> rows =
        support.lookupOptions(
            aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow(),
            field,
            TENANT_1,
            null,
            50,
            "9999");
    assertTrue(rows.isEmpty(), "陌生人不应看到任何候选（同租户他人行被过滤）");
  }

  @Test
  @DisplayName("lookup 过滤：他人候选的创建者本人可见自己的行（默认 owner 双向一致）")
  void lookup_otherOwnerSeesOwnRow() {
    RuntimeFieldColumn field =
        aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow().columns().get(1);
    List<Map<String, Object>> rows =
        support.lookupOptions(
            aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow(),
            field,
            TENANT_1,
            null,
            50,
            "9009");
    assertEquals(1, rows.size(), "9009 只应看到自己的候选");
    assertEquals("他人候选", rows.get(0).get("name"));
  }

  @Test
  @DisplayName("lookup 豁免：非数值主体（如 username）不启用行级过滤（与主行口径一致）")
  void lookup_nonNumericExempt() {
    RuntimeFieldColumn field =
        aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow().columns().get(1);
    List<Map<String, Object>> rows =
        support.lookupOptions(
            aclCatalog().findPublishedRuntime("acl_source", TENANT_1).orElseThrow(),
            field,
            TENANT_1,
            null,
            50,
            "zhangsan");
    assertEquals(3, rows.size(), "非数值主体按既有审计语义跳过行级过滤");
  }
}
