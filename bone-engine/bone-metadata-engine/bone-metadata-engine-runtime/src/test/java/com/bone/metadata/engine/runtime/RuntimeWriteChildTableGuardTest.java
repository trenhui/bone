package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G10 写侧对齐回归：主行写载荷携带 {@code CHILD_TABLE} 声明字段必须显式拒绝（400）， 不允许静默丢弃、更不允许落 SQL 变 500。
 *
 * <p><b>背景（2026-10-21 全模块联调实测）</b>：{@code POST /runtime/entities/t_claim_case_e2e_xxx/records} 载荷带
 * {@code items}（CHILD_TABLE）→ 该伪列直接进 INSERT → MySQL {@code Unknown column 'items' in 'field list'}
 * → 被 catch-all 兜成 <b>500 COMMON_INTERNAL_ERROR</b>。读侧 {@code RuntimeQuerySupport} 对 CHILD_TABLE
 * 的过滤/排序早已显式拒绝（G10「静默忽略会返回误导性结果，比报错危险」）， 写侧此前漏了对齐。
 *
 * <p><b>为什么是 400 而不是静默忽略</b>：静默丢弃等于调用方提交的明细数据无声丢失（比报错危险）； UI 主表单本就不提交 CHILD_TABLE（明细走子表端点整组替换），
 * 本守卫拦的是 API 直写与第三方集成——给 400 + 指引改走子表端点是唯一不丢数据也不误导的口径。
 */
class RuntimeWriteChildTableGuardTest {

  private static PublishedRuntimeEntity claimEntity() {
    return new PublishedRuntimeEntity(
        "t_claim_case_e2e",
        "t_t_claim_case_e2e",
        "id",
        0L,
        List.of(
            new RuntimeFieldColumn("policy_no", "STRING", false, false, false),
            new RuntimeFieldColumn("invoice_amount", "DECIMAL", false, false, false),
            // CHILD_TABLE 声明字段：无物理列（G10）
            new RuntimeFieldColumn("items", "CHILD_TABLE", false, false, false)),
        null,
        null,
        null,
        null);
  }

  @Test
  @DisplayName("主行载荷携带 CHILD_TABLE 字段 → META_RUNTIME_VALIDATION_FAILED，消息指引子表端点")
  void childTableFieldRejected() {
    RuntimeRecordException ex =
        assertThrows(
            RuntimeRecordException.class,
            () ->
                JdbcRuntimeRecordService.filterWritable(
                    claimEntity(),
                    Map.of(
                        "policy_no",
                        "POL-1",
                        "invoice_amount",
                        1000,
                        "items",
                        List.of(Map.of("name", "诊查费", "amount", 1000)))));

    assertEquals(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, ex.getErrorCode());
    assertTrue(
        ex.getMessage().contains("items") && ex.getMessage().contains("CHILD_TABLE"),
        () -> "消息应指明CHILD_TABLE字段与原因: " + ex.getMessage());
    assertTrue(ex.getMessage().contains("/children/"), () -> "消息应指引改走子表端点: " + ex.getMessage());
  }

  @Test
  @DisplayName("不带 CHILD_TABLE 的载荷正常过滤：保留可写列、剔除系统列")
  void normalPayloadStillWritable() {
    Map<String, Object> out =
        JdbcRuntimeRecordService.filterWritable(
            claimEntity(),
            Map.of(
                "policy_no",
                "POL-1",
                "invoice_amount",
                1000,
                "unknown_field",
                "x",
                "tenant_id",
                7L));

    assertEquals("POL-1", out.get("policy_no"));
    assertEquals(1000, out.get("invoice_amount"));
    assertFalse(out.containsKey("unknown_field"), "声明外字段必须被过滤");
    assertFalse(out.containsKey("tenant_id"), "系统列必须被过滤");
  }
}
