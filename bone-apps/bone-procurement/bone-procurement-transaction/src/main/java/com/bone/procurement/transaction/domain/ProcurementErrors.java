package com.bone.procurement.transaction.domain;

import com.bone.core.exception.BizException;

/** 采购交易错误工厂：稳定业务码 → HTTP 状态的唯一映射（禁止在 application/adapter 裸抛）。 */
public final class ProcurementErrors {

  private ProcurementErrors() {}

  public static BizException of(String errorCode, Object detail) {
    return new BizException(httpStatusOf(errorCode), message(errorCode, detail), errorCode);
  }

  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(httpStatusOf(errorCode), message(errorCode, detail), errorCode, cause);
  }

  private static int httpStatusOf(String code) {
    if (ProcurementErrorCodes.VENDOR_NOT_ACTIVE.equals(code)) return 400;
    if (ProcurementErrorCodes.LEGAL_ENTITY_MISMATCH.equals(code)) return 400;
    if (ProcurementErrorCodes.PO_AMOUNT_MISMATCH.equals(code)) return 400;
    if (ProcurementErrorCodes.PRICE_SNAPSHOT_MISSING.equals(code)) return 409;
    if (ProcurementErrorCodes.TERMINAL_WRITE_BACK.equals(code)) return 409;
    if (ProcurementErrorCodes.INVALID_TRANSITION.equals(code)) return 409;
    return 400;
  }

  private static String message(String code, Object detail) {
    return "[" + code + "] " + String.valueOf(detail);
  }
}
