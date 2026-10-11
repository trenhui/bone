package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * G10 关系能力单测：引用（lookup）显示值补全、引用存在性校验、子表 CRUD 与级联软删、附件上限、配置解析。
 *
 * <p>测试范式沿用 {@link JdbcRuntimeRecordServiceTest}：H2 MySQL 兼容模式 + {@code DATABASE_TO_LOWER} + 内联建表 +
 * lambda 伪造 catalog， 使反引号语法与 列标签大小写语义对齐生产 MySQL。
 *
 * <p>场景取<b>费用报销真实形态</b>：报销单（父）→ 费用明细（子表，多行）+ 费用类别（引用主数据）。
 */
class RuntimeRelationSupportTest {

  private static final long TENANT = 1L;

  private NamedParameterJdbcTemplate jdbc;
  private RuntimeRelationSupport relationSupport;
  private JdbcRuntimeRecordService recordService;

  @BeforeEach
  void setUp() {
    RuntimeRelationSpec.clearCache();
    var url = "jdbc:h2:mem:rel_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    jdbc = new NamedParameterJdbcTemplate(ds);

    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS rel_claim");
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS rel_item");
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS rel_category");
    // 费用类别主数据
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE rel_category ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`name` VARCHAR(64) NOT NULL, `version` INT NOT NULL DEFAULT 0, "
                + "`deleted` SMALLINT NOT NULL DEFAULT 0)");
    // 报销单（父）：含引用字段 category_id、子表字段 items、附件字段 files
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE rel_claim ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`claim_no` VARCHAR(64) NOT NULL, `amount` DOUBLE, "
                + "`category_id` BIGINT, `files` VARCHAR(2000), "
                + "`version` INT NOT NULL DEFAULT 0, `deleted` SMALLINT NOT NULL DEFAULT 0)");
    // 费用明细（子表）：claim_id 为外键
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE rel_item ("
                + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
                + "`claim_id` BIGINT NOT NULL, `sort_order` INT, `note` VARCHAR(255), "
                + "`amount` DOUBLE, `category_id` BIGINT, "
                + "`version` INT NOT NULL DEFAULT 0, `deleted` SMALLINT NOT NULL DEFAULT 0)");

    relationSupport = new RuntimeRelationSupport(jdbc, catalog());
    recordService =
        new JdbcRuntimeRecordService(
            jdbc,
            catalog(),
            RuntimeAuditSink.NOOP,
            RuntimeStateEntryResolver.NONE,
            relationSupport);
  }

  /** catalog：三个实体 + 关系声明（零 DDL——关系配置放在 validation_rules JSON）。 */
  private RuntimeEntityCatalog catalog() {
    return (code, tenantId) -> {
      switch (code) {
        case "rel_category":
          return Optional.of(
              new PublishedRuntimeEntity(
                  "rel_category",
                  "rel_category",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("name", "STRING", true, false, false))));
        case "rel_item":
          return Optional.of(
              new PublishedRuntimeEntity(
                  "rel_item",
                  "rel_item",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("claim_id", "LONG", true, false, false),
                      new RuntimeFieldColumn("sort_order", "INTEGER", false, false, false),
                      new RuntimeFieldColumn("note", "STRING", true, false, false),
                      new RuntimeFieldColumn(
                          "amount", "DECIMAL", false, false, false, null, null, "{\"min\":0.01}"),
                      new RuntimeFieldColumn(
                          "category_id",
                          RuntimeRelationSpec.TYPE_REFERENCE,
                          false,
                          false,
                          false,
                          null,
                          null,
                          referenceRules("rel_category")))));
        case "rel_claim":
          return Optional.of(
              new PublishedRuntimeEntity(
                  "rel_claim",
                  "rel_claim",
                  "id",
                  tenantId,
                  List.of(
                      new RuntimeFieldColumn("id", "LONG", true, false, true),
                      new RuntimeFieldColumn("claim_no", "STRING", true, false, false),
                      new RuntimeFieldColumn("amount", "DECIMAL", false, false, false),
                      new RuntimeFieldColumn(
                          "category_id",
                          RuntimeRelationSpec.TYPE_REFERENCE,
                          false,
                          false,
                          false,
                          null,
                          null,
                          referenceRules("rel_category")),
                      new RuntimeFieldColumn(
                          "items",
                          RuntimeRelationSpec.TYPE_CHILD_TABLE,
                          false,
                          false,
                          false,
                          null,
                          null,
                          "{\"childTable\":{\"childEntityCode\":\"rel_item\",\"foreignKey\":\"claim_id\","
                              + "\"orderColumn\":\"sort_order\",\"cascadeDelete\":true,\"maxRows\":50}}"),
                      new RuntimeFieldColumn(
                          "files",
                          RuntimeRelationSpec.TYPE_ATTACHMENT,
                          false,
                          false,
                          false,
                          null,
                          null,
                          "{\"attachment\":{\"maxCount\":2}}"))));
        default:
          return Optional.empty();
      }
    };
  }

  private static String referenceRules(String target) {
    return "{\"reference\":{\"targetEntityCode\":\"" + target + "\",\"labelField\":\"name\"}}";
  }

  private void seedCategory(long id, String name) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", id);
    row.put("tenant_id", TENANT);
    row.put("name", name);
    row.put("deleted", 0);
    row.put("version", 0);
    jdbc.update(
        "INSERT INTO rel_category (`id`,`tenant_id`,`name`,`deleted`,`version`) VALUES (:id,:tenant_id,:name,:deleted,:version)",
        row);
  }

  private void seedClaim(long id, String claimNo, Object categoryId) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", id);
    row.put("tenant_id", TENANT);
    row.put("claim_no", claimNo);
    row.put("amount", 100.0);
    row.put("category_id", categoryId);
    row.put("deleted", 0);
    row.put("version", 0);
    jdbc.update(
        "INSERT INTO rel_claim (`id`,`tenant_id`,`claim_no`,`amount`,`category_id`,`deleted`,`version`) "
            + "VALUES (:id,:tenant_id,:claim_no,:amount,:category_id,:deleted,:version)",
        row);
  }

  // ===================== 引用：显示值补全 =====================

  @Test
  void shouldEnrichReferenceLabelOnGetById() {
    seedCategory(11L, "差旅");
    seedClaim(501L, "C-1", 11L);

    Map<String, Object> loaded = recordService.getById("rel_claim", TENANT, "501");

    assertEquals("差旅", loaded.get("category_id__label"), "详情应带引用显示值");
    assertEquals(11L, ((Number) loaded.get("category_id")).longValue(), "原值保留");
  }

  @Test
  void shouldEnrichReferenceLabelsInBatchWithoutNPlusOne() {
    seedCategory(11L, "差旅");
    seedCategory(12L, "办公");
    seedClaim(502L, "C-2", 11L);
    seedClaim(503L, "C-3", 12L);
    seedClaim(504L, "C-4", 11L);

    var page =
        recordService.page(
            "rel_claim", TENANT, 1, 10, RuntimePageQuery.parse("claim_no,category_id", null, null));

    assertEquals(3, page.getList().size());
    Map<Object, Object> seen = new LinkedHashMap<>();
    page.getList().forEach(r -> seen.put(r.get("claim_no"), r.get("category_id__label")));
    assertEquals("差旅", seen.get("C-2"), "批量补全应正确映射");
    assertEquals("办公", seen.get("C-3"));
    assertEquals("差旅", seen.get("C-4"));
  }

  @Test
  void shouldSkipLabelWhenTargetNotResolvable() {
    // 指向不存在的引用值：不报错，label 缺失由前端降级展示
    seedClaim(505L, "C-5", 999L);

    Map<String, Object> loaded = recordService.getById("rel_claim", TENANT, "505");

    assertEquals(999L, ((Number) loaded.get("category_id")).longValue());
    assertNull(loaded.get("category_id__label"));
  }

  // ===================== 引用：写入校验 =====================

  @Test
  void shouldRejectReferenceToMissingRecord() {
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                recordService.create(
                    "rel_claim", TENANT, Map.of("claim_no", "C-BAD", "category_id", 999L), 601L));
    assertEquals(MetadataErrorCodes.RUNTIME_REFERENCE_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void shouldAcceptReferenceToExistingRecord() {
    seedCategory(11L, "差旅");

    Map<String, Object> created =
        recordService.create(
            "rel_claim", TENANT, Map.of("claim_no", "C-OK", "category_id", 11L), 602L);

    assertEquals(11L, ((Number) created.get("category_id")).longValue());
  }

  // ===================== 子表：CRUD =====================

  @Test
  void shouldReplaceChildrenAndInsertNewRows() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-CHILD"), 701L);
    seedCategory(11L, "差旅");
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("note", "机票");
    r1.put("amount", 800.0);
    r1.put("category_id", 11L);
    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("note", "酒店");
    r2.put("amount", 1200.0);
    r2.put("category_id", 11L);

    var saved =
        relationSupport.replaceChildren(parent(), TENANT, "701", childSpec(), List.of(r1, r2));

    assertEquals(2, saved.size());
    var rows = relationSupport.listChildren(parent(), TENANT, "701", childSpec());
    // 子行内引用补全在 Controller 层（listChildren 裸查询 + enrich），此处显式调用以对齐生产语义
    relationSupport.enrichReferenceLabels(child(), TENANT, rows);
    assertEquals(2, rows.size());
    List<Object> orders = rows.stream().map(r -> r.get("sort_order")).toList();
    assertTrue(orders.contains(1) && orders.contains(2), "顺序列应被赋值: " + orders);
    rows.forEach(r -> assertEquals(701L, ((Number) r.get("claim_id")).longValue(), "外键回填"));
    assertEquals("差旅", rows.get(0).get("category_id__label"), "子行内引用也应补全");
  }

  @Test
  void shouldSoftDeleteOrphanChildrenOnReplace() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-ORPHAN"), 702L);
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("note", "A");
    Map<String, Object> r2 = new LinkedHashMap<>();
    r2.put("note", "B");
    relationSupport.replaceChildren(parent(), TENANT, "702", childSpec(), List.of(r1, r2));
    assertEquals(2, relationSupport.listChildren(parent(), TENANT, "702", childSpec()).size());

    // 第二次只提交 1 行 → 另一行应被软删而非残留
    Map<String, Object> r3 = new LinkedHashMap<>();
    r3.put("note", "C");
    relationSupport.replaceChildren(parent(), TENANT, "702", childSpec(), List.of(r3));

    var remaining = relationSupport.listChildren(parent(), TENANT, "702", childSpec());
    assertEquals(1, remaining.size(), "只保留提交的行");
    assertEquals("C", remaining.get(0).get("note"));
  }

  @Test
  void shouldPreserveExistingChildRowIdOnUpdate() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-UPD"), 703L);
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("note", "原值");
    var saved = relationSupport.replaceChildren(parent(), TENANT, "703", childSpec(), List.of(r1));
    Object childId = saved.get(0).get("id");

    Map<String, Object> upd = new LinkedHashMap<>();
    upd.put("id", childId);
    upd.put("note", "改后");
    relationSupport.replaceChildren(parent(), TENANT, "703", childSpec(), List.of(upd));

    var rows = relationSupport.listChildren(parent(), TENANT, "703", childSpec());
    assertEquals(1, rows.size());
    assertEquals(childId, rows.get(0).get("id"), "主键不变（更新而非重建）");
    assertEquals("改后", rows.get(0).get("note"));
  }

  @Test
  void shouldRejectChildRowMissingRequiredField() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-REQ"), 706L);
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("amount", 100.0); // note 为必填但缺失

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                relationSupport.replaceChildren(parent(), TENANT, "706", childSpec(), List.of(r1)));
    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("note"), "报错应指明缺失字段: " + ex.getMessage());
    assertEquals(
        0, relationSupport.listChildren(parent(), TENANT, "706", childSpec()).size(), "校验失败不落行");
  }

  @Test
  void shouldRejectChildRowBelowMinAmount() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-MIN"), 707L);
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("note", "违规金额");
    r1.put("amount", 0.0); // 违反 validation_rules min:0.01

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                relationSupport.replaceChildren(parent(), TENANT, "707", childSpec(), List.of(r1)));
    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("0.01"), "报错应指明下限: " + ex.getMessage());
  }

  @Test
  void shouldIgnoreForeignKeyTamperingInChildPayload() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-P1"), 704L);
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-P2"), 705L);
    Map<String, Object> evil = new LinkedHashMap<>();
    evil.put("note", "x");
    evil.put("claim_id", 705L); // 试图把行挂到别的父单

    relationSupport.replaceChildren(parent(), TENANT, "704", childSpec(), List.of(evil));

    var rows = relationSupport.listChildren(parent(), TENANT, "704", childSpec());
    assertEquals(1, rows.size());
    assertEquals(704L, ((Number) rows.get(0).get("claim_id")).longValue(), "外键不被载荷改写");
  }

  @Test
  void shouldCascadeSoftDeleteChildrenWhenParentDeleted() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-DEL"), 706L);
    Map<String, Object> r1 = new LinkedHashMap<>();
    r1.put("note", "子项");
    relationSupport.replaceChildren(parent(), TENANT, "706", childSpec(), List.of(r1));

    recordService.delete("rel_claim", TENANT, "706");

    assertTrue(
        relationSupport.listChildren(parent(), TENANT, "706", childSpec()).isEmpty(), "子行应级联软删");
    Long alive =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rel_item WHERE claim_id = 706 AND deleted = 0",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(),
            Long.class);
    assertEquals(0L, alive);
  }

  @Test
  void shouldRejectChildOperationWhenParentMissing() {
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                relationSupport.replaceChildren(
                    parent(), TENANT, "999999", childSpec(), List.of(Map.of("note", "x"))));
    assertEquals(MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, ex.getErrorCode());
  }

  @Test
  void shouldRejectChildRowsExceedingMaxRows() {
    recordService.create("rel_claim", TENANT, Map.of("claim_no", "C-MAX"), 707L);
    List<Map<String, Object>> tooMany = new java.util.ArrayList<>();
    for (int i = 0; i < 51; i++) {
      tooMany.add(Map.of("note", "n" + i));
    }

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () -> relationSupport.replaceChildren(parent(), TENANT, "707", childSpec(), tooMany));

    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
  }

  // ===================== 附件：数量上限 =====================

  @Test
  void shouldRejectAttachmentCountExceedingMax() {
    // files 字段声明 maxCount=2
    String filesJson = "[{\"objectName\":\"a\"},{\"objectName\":\"b\"},{\"objectName\":\"c\"}]";

    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                recordService.create(
                    "rel_claim", TENANT, Map.of("claim_no", "C-FILE", "files", filesJson), 801L));

    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
  }

  @Test
  void shouldAcceptAttachmentWithinMax() {
    String filesJson = "[{\"objectName\":\"a\"},{\"objectName\":\"b\"}]";

    Map<String, Object> created =
        recordService.create(
            "rel_claim", TENANT, Map.of("claim_no", "C-FILE2", "files", filesJson), 802L);

    assertNotNull(created.get("files"));
  }

  // ===================== 配置解析 =====================

  @Test
  void shouldParseAllThreeRelationTypes() {
    assertTrue(RuntimeRelationSpec.of(column(parent(), "category_id")).isReference());
    assertTrue(RuntimeRelationSpec.of(column(parent(), "items")).isChildTable());
    assertTrue(RuntimeRelationSpec.of(column(parent(), "files")).isAttachment());
    assertFalse(RuntimeRelationSpec.of(column(parent(), "claim_no")).declared(), "普通字段无声明");
  }

  @Test
  void shouldDegradeWhenRelationConfigMissing() {
    // 类型声明 REFERENCE 但 validation_rules 为空 → 降级为「无声明」，不抛错
    RuntimeFieldColumn broken =
        new RuntimeFieldColumn("x", RuntimeRelationSpec.TYPE_REFERENCE, false, false, false);

    assertFalse(RuntimeRelationSpec.of(broken).declared());
  }

  @Test
  void shouldDegradeWhenRelationConfigMalformedJson() {
    RuntimeFieldColumn broken =
        new RuntimeFieldColumn(
            "x", RuntimeRelationSpec.TYPE_REFERENCE, false, false, false, null, null, "{not-json");

    assertFalse(RuntimeRelationSpec.of(broken).declared());
  }

  // ===================== 工具 =====================

  private PublishedRuntimeEntity parent() {
    return catalog().findPublishedRuntime("rel_claim", TENANT).orElseThrow();
  }

  private PublishedRuntimeEntity child() {
    return catalog().findPublishedRuntime("rel_item", TENANT).orElseThrow();
  }

  private RuntimeRelationSpec childSpec() {
    return RuntimeRelationSpec.of(column(parent(), "items"));
  }

  private RuntimeFieldColumn column(PublishedRuntimeEntity entity, String code) {
    return entity.columns().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
  }
}
