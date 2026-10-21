package com.bone.app.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ExpenseAdvanceConfirmPayoutHandler} 借款放款逃逸：入参守卫 + 流水号前缀与报销打款区分（ADV- vs PAY-）。 */
class ExpenseAdvanceConfirmPayoutHandlerTest {

  private final ExpenseAdvanceConfirmPayoutHandler handler =
      new ExpenseAdvanceConfirmPayoutHandler();

  private static RuntimeActionContext ctx(Map<String, Object> params) {
    return new RuntimeActionContext("expense_advance", 0L, "1", null, params, "u1");
  }

  @Test
  @DisplayName("漏传 channel → 400 META_RUNTIME_ACTION_INVALID_PARAMS（不禁言 NPE→500）")
  void missingChannel_failsFast400() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("note", "出差备用金");

    assertThatThrownBy(() -> handler.execute(ctx(params)))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS))
        .hasMessageContaining("channel");
  }

  @Test
  @DisplayName("channel + note 齐全 → 生成 ADV- 前缀放款流水，与报销 PAY- 前缀可区分")
  void happyPath_generatesAdvanceSerial() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("channel", "PAYROLL");
    params.put("note", "Q4 项目备用金");

    Map<String, Object> result = handler.execute(ctx(params));

    assertThat(result).containsEntry("channel", "PAYROLL");
    assertThat(result.get("paymentSerial").toString()).startsWith("ADV-");
    assertThat(result).containsEntry("note", "Q4 项目备用金");
    assertThat(result.get("paidAt")).isNotNull();
  }
}
