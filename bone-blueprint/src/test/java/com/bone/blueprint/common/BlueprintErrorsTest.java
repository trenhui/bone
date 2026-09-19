package com.bone.blueprint.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BlueprintErrors} 的契约测试：配对的完整性与抛出形态。
 *
 * <p>重点是<strong>完整性</strong>——「码 → 状态」表漏登记不会在编译期暴露，且后果是异常被兜底成 400/500（污染 SLO 口径）。本测试与 {@code
 * BlueprintErrors} 的静态校验双保险：静态校验保证运行期不可能漏，本测试在 CI 给出可读的失败信息。
 */
class BlueprintErrorsTest {

  @Test
  @DisplayName("BlueprintErrorCodes 的每个码常量都能解析出合法的 HTTP 状态（无遗漏登记）")
  void everyDeclaredCodeHasRegisteredStatus() throws IllegalAccessException {
    int checked = 0;
    for (Field field : BlueprintErrorCodes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
        continue;
      }
      String code = (String) field.get(null);
      int status = BlueprintErrors.httpStatusOf(code);
      assertTrue(
          status >= 400 && status <= 599, "错误码 " + field.getName() + " 的默认状态不在 4xx/5xx：" + status);
      checked++;
    }
    assertTrue(checked >= 9, "应至少覆盖 9 个错误码常量，实际 " + checked);
  }

  @Test
  @DisplayName("HTTP 状态取自配对表，而非抛出点手写")
  void statusComesFromPairingTable() {
    assertEquals(404, BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, 1L).getCode());
    assertEquals(409, BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT).getCode());
    assertEquals(400, BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_INVALID).getCode());
    assertEquals(409, BlueprintErrors.of(BlueprintErrorCodes.ORDER_STOCK_INSUFFICIENT).getCode());
    assertEquals(404, BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_NOT_FOUND).getCode());
    assertEquals(401, BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_SIGNATURE_INVALID).getCode());
    assertEquals(
        502, BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_CHANNEL_PREPAY_FAILED).getCode());
    assertEquals(
        403, BlueprintErrors.of(BlueprintErrorCodes.PAYMENT_CALLBACK_SOURCE_NOT_ALLOWED).getCode());
    assertEquals(409, BlueprintErrors.of(BlueprintErrorCodes.IDEMPOTENCY_CONFLICT).getCode());
  }

  @Test
  @DisplayName("消息形态为「码: 上下文」，无上下文时只留码（前端按码聚合，文案改动不影响口径）")
  void messageComposition() {
    assertEquals(
        "BP_ORDER_NOT_FOUND: 42",
        BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND, 42L).getMessage());
    assertEquals(
        "BP_ORDER_NOT_FOUND", BlueprintErrors.of(BlueprintErrorCodes.ORDER_NOT_FOUND).getMessage());
  }

  @Test
  @DisplayName("cause 透传：状态冲突类错误必须保留领域异常的根因")
  void causeIsPropagated() {
    RuntimeException root = new IllegalStateException("已发货，不可取消");
    BizException ex =
        BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, root.getMessage(), root);
    assertSame(root, ex.getCause());
    assertEquals("BP_ORDER_STATUS_CONFLICT: 已发货，不可取消", ex.getMessage());
  }

  @Test
  @DisplayName("supplier 延迟构造：orElseThrow 未命中时不构造异常")
  void supplierIsLazy() {
    Supplier<BizException> supplier =
        BlueprintErrors.supplier(BlueprintErrorCodes.ORDER_NOT_FOUND, 7L);
    assertTrue(supplier.get() instanceof BizException);
    assertEquals("BP_ORDER_NOT_FOUND: 7", supplier.get().getMessage());
  }

  @Test
  @DisplayName("未登记的错误码立即失败，不静默兜底成 400")
  void unregisteredCodeFailsFast() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> BlueprintErrors.httpStatusOf("BP_NOT_REGISTERED"));
    assertTrue(ex.getMessage().contains("BP_NOT_REGISTERED"), ex.getMessage());
  }
}
