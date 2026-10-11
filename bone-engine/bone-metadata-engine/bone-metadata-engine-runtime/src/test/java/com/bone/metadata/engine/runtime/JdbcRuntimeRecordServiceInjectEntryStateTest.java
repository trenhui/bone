package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * G14 v2 契约的 engine 写路径测试：入口状态注入发生在唯一写出口（{@code create}）内，且在 「422 保护」之后、「必填校验」之前。
 *
 * <p>负向探针：删掉 {@code JdbcRuntimeRecordService.injectEntryState} 的 {@code payload.put(stateField,
 * entry)} → {@link #create_withoutStatus_platformInjectsEntryState()} 红；把注入挪到 422 判定之前 → {@link
 * #create_withClientStatus_stillRejected422()} 红（客户端值先被拒，注入不得覆盖保护）。
 */
class JdbcRuntimeRecordServiceInjectEntryStateTest {

  private static final String DDL =
      "CREATE TABLE demo_claim ("
          + "`id` BIGINT PRIMARY KEY, `tenant_id` BIGINT NOT NULL, "
          + "`title` VARCHAR(128) NOT NULL, `status` VARCHAR(32) NOT NULL, "
          + "`amount` DOUBLE, `version` INT NOT NULL DEFAULT 0, "
          + "`deleted` SMALLINT NOT NULL DEFAULT 0)";

  private static RuntimeEntityCatalog claimCatalog() {
    return (code, tenantId) ->
        Optional.of(
            new PublishedRuntimeEntity(
                "demo_claim",
                "demo_claim",
                "id",
                tenantId,
                List.of(
                    new RuntimeFieldColumn("id", "LONG", true, false, true),
                    new RuntimeFieldColumn("title", "STRING", true, false, false),
                    new RuntimeFieldColumn("status", "STRING", true, false, false),
                    new RuntimeFieldColumn("amount", "DOUBLE", false, false, false)),
                "status"));
  }

  private static NamedParameterJdbcTemplate h2() {
    var url = "jdbc:h2:mem:entry_state;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    var ds = new SimpleDriverDataSource(new Driver(), url);
    var jdbc = new NamedParameterJdbcTemplate(ds);
    jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS demo_claim");
    jdbc.getJdbcTemplate().execute(DDL);
    return jdbc;
  }

  private static JdbcRuntimeRecordService service(
      NamedParameterJdbcTemplate jdbc, RuntimeStateEntryResolver resolver) {
    return new JdbcRuntimeRecordService(jdbc, claimCatalog(), RuntimeAuditSink.NOOP, resolver);
  }

  @Test
  void create_withoutStatus_platformInjectsEntryState() {
    JdbcRuntimeRecordService svc = service(h2(), (entityCode, tenantId) -> "DRAFT");
    Map<String, Object> created = svc.create("demo_claim", 1L, Map.of("title", "T1"), 1001L);
    assertEquals("DRAFT", created.get("status"), "平台必须在写路径注入入口状态（否则必填的受保护状态列会死锁）");
  }

  @Test
  void create_withClientStatus_stillRejected422() {
    // 关键顺序契约：422 保护先于注入，注入绝不能帮客户端「洗白」非法直写
    JdbcRuntimeRecordService svc = service(h2(), (entityCode, tenantId) -> "DRAFT");
    RuntimeRecordException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeRecordException.class,
            () -> svc.create("demo_claim", 1L, Map.of("title", "T2", "status", "PAID"), 1002L));
    assertEquals(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED, ex.getErrorCode(), "携带直写必须 422，与 resolver 无关");
  }

  @Test
  void create_resolverReturnsNull_failClosed() {
    // 入口不可推导（全环状态机）→ 不注入 → 必填校验拒绝（fail-closed），绝不采信客户端值
    JdbcRuntimeRecordService svc = service(h2(), (entityCode, tenantId) -> null);
    RuntimeRecordException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeRecordException.class,
            () -> svc.create("demo_claim", 1L, Map.of("title", "T3"), 1003L));
    assertEquals(
        MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode(), "无入口态时 fail-closed（必填拒绝）");
  }
}
