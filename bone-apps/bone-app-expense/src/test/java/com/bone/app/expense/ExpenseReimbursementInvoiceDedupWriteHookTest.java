package com.bone.app.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeWriteHookContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** {@link ExpenseReimbursementInvoiceDedupWriteHook}：一票多报拦截（G9）——租户内跨单查重、update 排除自身、空票号跳过。 */
@ExtendWith(MockitoExtension.class)
class ExpenseReimbursementInvoiceDedupWriteHookTest {

  private static final long TENANT = 7L;

  @Mock private NamedParameterJdbcTemplate jdbc;

  private ExpenseReimbursementInvoiceDedupWriteHook hook;

  @BeforeEach
  void setUp() {
    // 必须在 @BeforeEach 构造：字段初始化早于 MockitoExtension 的 @Mock 注入
    hook = new ExpenseReimbursementInvoiceDedupWriteHook(jdbc);
  }

  private static PublishedRuntimeEntity itemEntity() {
    return new PublishedRuntimeEntity(
        "expense_reimbursement_item", "expense_reimbursement_item", "id", TENANT, List.of());
  }

  private static RuntimeWriteHookContext ctx(String recordId, Map<String, Object> payload) {
    return new RuntimeWriteHookContext(itemEntity(), TENANT, recordId, payload, "u1");
  }

  private static Map<String, Object> invoicePayload(String invoiceNo) {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (invoiceNo != null) {
      payload.put("invoice_no", invoiceNo);
    }
    return payload;
  }

  private void stubCount(long count) {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(count);
  }

  @Test
  @DisplayName("新建明细发票号租户内已存在 → 422 一票多报拦截")
  void create_duplicateInvoice_blocked() {
    stubCount(1L);

    assertThatThrownBy(() -> hook.beforeCreate(ctx(null, invoicePayload("INV-2026-001"))))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("INV-2026-001");
  }

  @Test
  @DisplayName("发票号唯一 → 放行")
  void create_uniqueInvoice_passes() {
    stubCount(0L);

    assertThatNoException()
        .isThrownBy(() -> hook.beforeCreate(ctx(null, invoicePayload("INV-2026-002"))));
  }

  @Test
  @DisplayName("update 排除自身：改备注不动票号时自身不构成重复")
  void update_selfExcluded() {
    stubCount(0L);

    assertThatNoException()
        .isThrownBy(() -> hook.beforeUpdate(ctx("55", invoicePayload("INV-2026-002"))));
  }

  @Test
  @DisplayName("update 与他人明细撞号 → 422")
  void update_duplicateWithOtherRow_blocked() {
    stubCount(1L);

    assertThatThrownBy(() -> hook.beforeUpdate(ctx("55", invoicePayload("INV-2026-001"))))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("一票多报");
  }

  @Test
  @DisplayName("空票号/无票行 → 不查重（不发查询）")
  void blankInvoice_skips() {
    assertThatNoException().isThrownBy(() -> hook.beforeCreate(ctx(null, invoicePayload(null))));
    assertThatNoException().isThrownBy(() -> hook.beforeCreate(ctx(null, invoicePayload("  "))));
    verify(jdbc, never())
        .queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
  }

  @Test
  @DisplayName("查重 SQL 按租户 + 未删行过滤，update 带自身排除")
  void sql_scopedByTenantDeletedAndSelf() {
    stubCount(0L);

    hook.beforeUpdate(ctx("55", invoicePayload("INV-2026-003")));

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
    String sql = sqlCaptor.getValue();
    assertThat(sql).contains("tenant_id").contains("deleted` = 0").contains("id` <> :selfId");

    ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).queryForObject(anyString(), paramsCaptor.capture(), eq(Long.class));
    assertThat(paramsCaptor.getValue().getValue("tenant")).isEqualTo(TENANT);
    assertThat(paramsCaptor.getValue().getValue("selfId")).isEqualTo("55");
  }
}
