package com.bone.app.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** {@link ExpenseAdvanceWriteHook}：借款单金额冻结兜底（主防线是平台字段锁，本钩子防种子漏跑的静默失效窗口）。 */
@ExtendWith(MockitoExtension.class)
class ExpenseAdvanceWriteHookTest {

  private static final long TENANT = 7L;

  @Mock private NamedParameterJdbcTemplate jdbc;

  private ExpenseAdvanceWriteHook hook;

  @BeforeEach
  void setUp() {
    hook = new ExpenseAdvanceWriteHook(jdbc);
  }

  private static RuntimeWriteHookContext ctx(String recordId, Map<String, Object> payload) {
    return new RuntimeWriteHookContext(
        new PublishedRuntimeEntity("expense_advance", "expense_advance", "id", TENANT, List.of()),
        TENANT,
        recordId,
        payload,
        "u1");
  }

  private static Map<String, Object> amountPayload(Object amount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("amount", amount);
    return payload;
  }

  private void stubStatus(String status) {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(status);
  }

  @Test
  @DisplayName("SUBMITTED/APPROVED/PAID/REJECTED/CANCELLED 态改金额 → 422（除 DRAFT 外全冻结）")
  void frozenStatuses_rejectAmountChange() {
    for (String status : new String[] {"SUBMITTED", "APPROVED", "PAID", "REJECTED", "CANCELLED"}) {
      stubStatus(status);
      assertThatThrownBy(() -> hook.beforeUpdate(ctx("9", amountPayload("100"))))
          .isInstanceOf(RuntimeRecordException.class)
          .satisfies(
              ex ->
                  assertThat(((RuntimeRecordException) ex).getErrorCode())
                      .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
          .hasMessageContaining(status);
    }
  }

  @Test
  @DisplayName("DRAFT 态改金额 → 放行（借款单无 Σ 规则，单笔金额编辑态可改）")
  void draft_amountChange_passes() {
    stubStatus("DRAFT");

    assertThatNoException().isThrownBy(() -> hook.beforeUpdate(ctx("9", amountPayload("100"))));
  }

  @Test
  @DisplayName("载荷不含 amount（纯状态/备注修改）→ 不触发任何查询")
  void nonAmountPayload_noQueries() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("remark", "备注");

    assertThatNoException().isThrownBy(() -> hook.beforeUpdate(ctx("9", payload)));
    verifyNoInteractions(jdbc);
  }

  @Test
  @DisplayName("实体锚定 expense_advance（与报销单钩子各管各实体，互不越界）")
  void entityCode_isAdvance() {
    assertThat(hook.entityCode()).isEqualTo("expense_advance");
  }
}
