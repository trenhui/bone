package com.bone.procurement.fulfillment.domain;

import com.bone.core.exception.BizException;

/** 采购履约错误工厂：稳定业务码 → HTTP 状态的唯一映射。 */
public final class FulfillmentErrors {

  private FulfillmentErrors() {}

  public static BizException of(String errorCode, Object detail) {
    return new BizException(httpStatusOf(errorCode), message(errorCode, detail), errorCode);
  }

  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(httpStatusOf(errorCode), message(errorCode, detail), errorCode, cause);
  }

  private static int httpStatusOf(String code) {
    if (FulfillmentErrorCodes.VENDOR_SCOPE_FORBIDDEN.equals(code)) return 403;
    if (FulfillmentErrorCodes.INVALID_TRANSITION.equals(code)) return 409;
    if (FulfillmentErrorCodes.ASN_GR_MISMATCH.equals(code)) return 409;
    return 400;
  }

  private static String message(String code, Object detail) {
    return "[" + code + "] " + String.valueOf(detail);
  }
}
