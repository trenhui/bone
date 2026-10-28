package com.bone.claim.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务错误码 → HTTP 状态的唯一配对真源 + {@link BizException} 统一工厂（blueprint 样板同款）。
 *
 * <p>新增错误码必须同步登记 {@link #DEFAULT_HTTP_STATUS}，否则类加载即抛 {@link IllegalStateException}（fail-fast）。
 */
public final class ClaimErrors {

  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          Map.entry(ClaimErrorCodes.CLAIM_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE, 422),
          Map.entry(ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD, 422),
          Map.entry(ClaimErrorCodes.CLAIM_INSURED_MISMATCH, 403),
          Map.entry(ClaimErrorCodes.CLAIM_REJECT_REASON_REQUIRED, 400),
          Map.entry(ClaimErrorCodes.CLAIM_REVIEWER_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_DUPLICATED, 409),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_LIMIT_EXCEEDED, 400),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_LOCKED, 409),
          Map.entry(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, 422),
          Map.entry(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_SETTLEMENT_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_SETTLEMENT_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_APPEAL_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_DECISION_ACTIVE_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_ADJUST_EVIDENCE_NOT_LOCKED, 422),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_INSUFFICIENT, 422),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_STATE_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_INSPECTION_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_INSPECTION_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_DECISION_NOT_FOUND, 404));

  static {
    checkEveryCodeRegistered();
  }

  private ClaimErrors() {}

  public static BizException of(String errorCode) {
    return of(errorCode, null, null);
  }

  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(
        httpStatusOf(errorCode), composeMessage(errorCode, detail), errorCode, cause);
  }

  public static Supplier<BizException> supplier(String errorCode, Object detail) {
    return () -> of(errorCode, detail);
  }

  public static int httpStatusOf(String errorCode) {
    Integer status = DEFAULT_HTTP_STATUS.get(errorCode);
    if (status == null) {
      throw new IllegalStateException("错误码未登记默认 HTTP 状态: " + errorCode);
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  private static void checkEveryCodeRegistered() {
    for (Field field : ClaimErrorCodes.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
        continue;
      }
      final String code;
      try {
        code = (String) field.get(null);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException("无法读取错误码常量: " + field.getName(), ex);
      }
      if (!DEFAULT_HTTP_STATUS.containsKey(code)) {
        throw new IllegalStateException(
            "错误码常量未在 ClaimErrors 登记默认 HTTP 状态: ClaimErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"");
      }
    }
  }
}
