package com.bone.app.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 采购付款逃逸处理器的判别力测试。
 *
 * <p><b>判别力纪律</b>：每个用例锚定「删掉实现哪一行会红」。仅断言「成功返回流水号」是假绿—— 一个永远返回固定 Map 的实现也能过，因此必须配负向用例（漏传 channel /
 * 金额非法 → 400 且带错误码）。
 */
class PurchaseOrderPayHandlerTest {

  private final PurchaseOrderPayHandler handler = new PurchaseOrderPayHandler();

  private static RuntimeActionContext ctx(Map<String, Object> params) {
    return new RuntimeActionContext("t_purchase_order", 0L, "1", null, params, "u1");
  }

  @Test
  @DisplayName("参数齐全 → 生成付款流水号与会计凭证号，金额原样回传")
  void execute_shouldReturnSerial_whenParamsComplete() {
    Map<String, Object> params = new HashMap<>();
    params.put("channel", "BANK");
    params.put("payAmount", new BigDecimal("26000.00"));
    params.put("note", "Q4 办公设备采购");

    Map<String, Object> r = handler.execute(ctx(params));

    assertTrue(String.valueOf(r.get("paymentSerial")).startsWith("POPAY-"), "付款流水号前缀应为 POPAY-");
    assertTrue(String.valueOf(r.get("voucherNo")).startsWith("VCH"), "应生成会计凭证号");
    assertEquals("BANK", r.get("channel"));
    assertEquals(new BigDecimal("26000.00"), r.get("payAmount"));
    assertEquals("Q4 办公设备采购", r.get("note"));
    assertNotNull(r.get("paidAt"), "应回传付款时间戳");
  }

  @Test
  @DisplayName("【负向】漏传 channel → 400 INVALID_PARAMS（不 500，且文案点名缺失入参）")
  void execute_shouldReject_whenChannelMissing() {
    Map<String, Object> params = new HashMap<>();
    params.put("payAmount", new BigDecimal("100"));

    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> handler.execute(ctx(params)));

    assertEquals(MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, ex.getErrorCode());
    assertTrue(ex.getMessage().contains("channel"), "错误信息须点名缺失的入参: " + ex.getMessage());
  }

  @Test
  @DisplayName("【负向】payAmount 为 0 / 负数 / 非数字 → 400 INVALID_PARAMS（防零金额与脏数据出金）")
  void execute_shouldReject_whenAmountInvalid() {
    Map<String, Object> zero = new HashMap<>();
    zero.put("channel", "BANK");
    zero.put("payAmount", new BigDecimal("0"));
    assertEquals(
        MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
        assertThrows(RuntimeRecordException.class, () -> handler.execute(ctx(zero)))
            .getErrorCode());

    Map<String, Object> negative = new HashMap<>();
    negative.put("channel", "BANK");
    negative.put("payAmount", new BigDecimal("-1"));
    assertEquals(
        MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
        assertThrows(RuntimeRecordException.class, () -> handler.execute(ctx(negative)))
            .getErrorCode());

    Map<String, Object> dirty = new HashMap<>();
    dirty.put("channel", "BANK");
    dirty.put("payAmount", "abc");
    assertEquals(
        MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
        assertThrows(RuntimeRecordException.class, () -> handler.execute(ctx(dirty)))
            .getErrorCode());
  }

  @Test
  @DisplayName("【负向】完全不传 params → 400 INVALID_PARAMS（不得 NPE 变 500）")
  void execute_shouldReject_whenParamsEmpty() {
    RuntimeRecordException ex =
        assertThrows(RuntimeRecordException.class, () -> handler.execute(ctx(new HashMap<>())));
    assertEquals(MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, ex.getErrorCode());
  }
}
