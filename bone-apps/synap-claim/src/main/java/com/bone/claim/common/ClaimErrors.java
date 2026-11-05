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
          // 400：请求体缺字段（两个保单标识都为空），不是业务前提不成立
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_KEY_REQUIRED, 400),
          // 422：号码存在但仅凭入参无法唯一确定归属 ⇒ 补平台号或合单，不是"保单不存在"
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_AMBIGUOUS, 422),
          // 422：两个保单号互相矛盾 ⇒ 先修正号对再收单
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_NUMBER_CONFLICT, 422),
          Map.entry(ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD, 422),
          Map.entry(ClaimErrorCodes.CLAIM_INSURED_MISMATCH, 403),
          Map.entry(ClaimErrorCodes.CLAIM_REJECT_REASON_REQUIRED, 400),
          Map.entry(ClaimErrorCodes.CLAIM_REVIEWER_CONFLICT, 409),
          // 422 而非 409：请求合法、操作人有权限，是"该主体已撤回同意、不得再处理其数据"这一前提不成立
          Map.entry(ClaimErrorCodes.CLAIM_CONSENT_WITHDRAWN, 422),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_DUPLICATED, 409),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_LIMIT_EXCEEDED, 400),
          Map.entry(ClaimErrorCodes.CLAIM_EVIDENCE_LOCKED, 409),
          Map.entry(ClaimErrorCodes.CLAIM_LIABILITY_NOT_FOUND, 422),
          Map.entry(ClaimErrorCodes.CLAIM_LIABILITY_INVALID_ARGUMENT, 400),
          Map.entry(ClaimErrorCodes.CLAIM_ADJUST_NOT_FOUND, 404),
          // 422 而非 400：请求合法，是"这一版理算已不作为出金依据"这一资金前提不成立
          Map.entry(ClaimErrorCodes.CLAIM_ADJUST_STATUS_INVALID, 422),
          Map.entry(ClaimErrorCodes.CLAIM_SETTLEMENT_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_SETTLEMENT_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_APPEAL_NOT_FOUND, 404),
          // 422 而非 400：请求本身合法，是"案件过了申诉窗口"这一业务前提不成立（与 POLICY_NOT_ACTIVE 同口径）
          Map.entry(ClaimErrorCodes.CLAIM_APPEAL_EXPIRED, 422),
          Map.entry(ClaimErrorCodes.CLAIM_DECISION_ACTIVE_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_ADJUST_EVIDENCE_NOT_LOCKED, 422),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_INSUFFICIENT, 422),
          Map.entry(ClaimErrorCodes.CLAIM_QUOTA_STATE_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_INSPECTION_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_INSPECTION_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.CLAIM_DECISION_NOT_FOUND, 404),
          // 配置面：404 = 目标不存在（防枚举统一语义）；409 = 唯一键冲突（重复创建）
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_DUPLICATED, 409),
          // 422 而非 400：请求本身合法，是"保单类型与被保人清单对不上"这一业务前提不成立
          Map.entry(ClaimErrorCodes.CLAIM_POLICY_TYPE_CONFLICT, 422),
          Map.entry(ClaimErrorCodes.CLAIM_SHARED_POOL_LIMIT_CONFLICT, 422),
          Map.entry(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, 400),
          Map.entry(ClaimErrorCodes.CLAIM_BIZ_IDENTITY_INVALID, 422),
          Map.entry(ClaimErrorCodes.CLAIM_PLAN_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_PLAN_DUPLICATED, 409),
          Map.entry(ClaimErrorCodes.CLAIM_COVERAGE_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_COVERAGE_DUPLICATED, 409),
          // 对账处置：404 = 差异不存在/不属本租户；409 = 已处置或结论非法（不静默幂等，见错误码注释）
          Map.entry(ClaimErrorCodes.CLAIM_RECON_DIFF_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.CLAIM_RECON_DIFF_CONFLICT, 409),
          // 400：调用方参数越界（时间窗/条数），不是业务前提不成立，也不是服务端故障
          Map.entry(ClaimErrorCodes.CLAIM_EVAL_SAMPLE_QUERY_INVALID, 400),
          Map.entry(ClaimErrorCodes.CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID, 400),
          // 400：时间窗缺失/倒置/超 31 天属调用方参数错，不是服务器故障（5xx 会污染 SLI 指标）
          Map.entry(ClaimErrorCodes.CLAIM_AI_ROLLOUT_QUERY_INVALID, 400),
          // 独立盲标：404 不存在/跨租户；409 状态冲突与标注人污染；422 载荷禁区命中（防反馈环闸门被绕过）
          Map.entry(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_NOT_FOUND, 404),
          Map.entry(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_CONFLICT, 409),
          Map.entry(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_PAYLOAD_FORBIDDEN, 422),
          Map.entry(ClaimErrorCodes.SC_CLAIM_BLIND_LABEL_ANNOTATOR_CONFLICT, 409));

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
