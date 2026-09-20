package com.bone.iam.common;

import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 业务错误码 → HTTP 状态 的<strong>唯一配对真源</strong>，以及抛出 {@link BizException} 的统一工厂。
 *
 * <p><b>为何需要它</b>：{@code BizException} 的第一个参数是 <em>HTTP 状态</em>（{@code GlobalExceptionHandler}
 * 用它设置响应状态），而<em>业务码</em>只能以字符串拼进 message——于是「码」与「状态」天然是两份数据。若在每个抛出点手写 {@code new BizException(404,
 * APPLICATION_NOT_FOUND + ": " + id)}，状态与码的配对就散落在全模块几十处，任一处不一致都没有机制发现 （错误码登记 §17 明文「禁止把 HTTP
 * 码与业务码混为同一个整数」）。
 *
 * <p><b>落地前的问题形态</b>：{@code AppApplicationService} / {@code ModuleApplicationService} 各自私藏 {@code
 * private static final int NOT_FOUND = 404}，并抛出无码的 {@code new BizException(NOT_FOUND, "应用不存在")}——
 * 既违反 §5「禁止无码抛出」，又让同一语义在模块内出现多份状态常量。
 *
 * <p><b>本类的做法</b>：把配对收进 {@link #DEFAULT_HTTP_STATUS} 一张表，抛出点只表达<strong>业务语义</strong>（哪个码 +
 * 什么上下文）。表与《Bone-错误码登记》§6 的 {@code IAM_} 表逐行对应，是代码侧的唯一副本。
 *
 * <p><b>新增错误码的强制约束</b>：{@link IamErrorCodes} 的每个 public String 常量都必须在表中登记状态，否则类加载即抛 {@link
 * IllegalStateException}——漏登记不可能溜到运行期（fail fast，而不是让异常被兜底成 400/500）。
 */
public final class IamErrors {

  /**
   * 码 → 默认 HTTP 状态（真源）。
   *
   * <p>与《Bone-错误码登记》§6 {@code IAM_} 表一一对应；{@link #checkEveryCodeRegistered()} 保证不会漏。
   */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          // 认证 / 令牌
          Map.entry(IamErrorCodes.LOGIN_FAILED, 401),
          Map.entry(IamErrorCodes.ACCOUNT_LOCKED, 423),
          Map.entry(IamErrorCodes.ACCOUNT_DISABLED, 403),
          Map.entry(IamErrorCodes.REFRESH_TOKEN_REUSE, 401),
          Map.entry(IamErrorCodes.REFRESH_TOKEN_REQUIRED, 400),
          Map.entry(IamErrorCodes.REFRESH_TOKEN_INVALID, 401),
          Map.entry(IamErrorCodes.REFRESH_TOKEN_ROTATE_FAILED, 500),
          // 密码
          Map.entry(IamErrorCodes.WEAK_PASSWORD, 400),
          Map.entry(IamErrorCodes.OLD_PASSWORD_MISMATCH, 401),
          // 账号
          Map.entry(IamErrorCodes.ACCOUNT_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.USERNAME_CONFLICT, 409),
          Map.entry(IamErrorCodes.ACCOUNT_STATUS_CONFLICT, 409),
          // 角色 / 权限
          Map.entry(IamErrorCodes.ROLE_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.ROLE_ID_REQUIRED, 400),
          Map.entry(IamErrorCodes.PERMISSION_NOT_FOUND, 404),
          // 会话
          Map.entry(IamErrorCodes.SESSION_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.SESSION_ID_REQUIRED, 400),
          // 组织
          Map.entry(IamErrorCodes.DEPT_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.MENU_NOT_FOUND, 404),
          // 审计
          Map.entry(IamErrorCodes.AUDIT_SETTINGS_REQUIRED, 400),
          // 当前账号自助
          Map.entry(IamErrorCodes.PROFILE_OWNERSHIP_DENIED, 401),
          // 租户
          Map.entry(IamErrorCodes.TENANT_ACCESS_DENIED, 403),
          Map.entry(IamErrorCodes.TENANT_DELETE_FORBIDDEN, 400),
          Map.entry(IamErrorCodes.TENANT_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.TENANT_CODE_CONFLICT, 409),
          Map.entry(IamErrorCodes.TENANT_QUOTA_EXCEEDED, 400),
          Map.entry(IamErrorCodes.TENANT_ID_REQUIRED, 400),
          Map.entry(IamErrorCodes.TENANT_QUOTA_INVALID, 400),
          // 应用 / 模块
          Map.entry(IamErrorCodes.APPLICATION_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.MODULE_NOT_FOUND, 404),
          Map.entry(IamErrorCodes.APPLICATION_ID_REQUIRED, 400),
          Map.entry(IamErrorCodes.USER_ID_REQUIRED, 400),
          Map.entry(IamErrorCodes.APP_ROLE_INVALID, 400),
          // 能力未启用
          Map.entry(IamErrorCodes.SSO_NOT_CONFIGURED, 501),
          Map.entry(IamErrorCodes.MFA_NOT_AVAILABLE, 501));

  static {
    checkEveryCodeRegistered();
  }

  private IamErrors() {}

  /** 抛业务异常，不带附加上下文。 */
  public static BizException of(String errorCode) {
    return of(errorCode, null, null);
  }

  /** 抛业务异常，附业务标识或上下文说明（拼成 {@code 码: 说明}）。 */
  public static BizException of(String errorCode, Object detail) {
    return of(errorCode, detail, null);
  }

  /** 抛业务异常，附上下文说明与根因。 */
  public static BizException of(String errorCode, Object detail, Throwable cause) {
    return new BizException(httpStatusOf(errorCode), composeMessage(errorCode, detail), cause);
  }

  /**
   * 供 {@code Optional.orElseThrow(...)} / {@code Optional.map(...)} 使用的延迟构造器。
   *
   * <p>用 Supplier 而非直接构造异常，是为了让「聚合不存在」这类正常分支<b>不付出构造开销</b>（含堆栈填充）。
   */
  public static Supplier<BizException> supplier(String errorCode, Object detail) {
    return () -> of(errorCode, detail);
  }

  /** 取错误码的默认 HTTP 状态；未登记即抛——这是本类的核心不变量，不做兜底。 */
  public static int httpStatusOf(String errorCode) {
    Integer status = DEFAULT_HTTP_STATUS.get(errorCode);
    if (status == null) {
      throw new IllegalStateException(
          "错误码未登记默认 HTTP 状态: "
              + errorCode
              + "；请在 IamErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被 GlobalExceptionHandler 兜底成 400。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link IamErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : IamErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 IamErrors 登记默认 HTTP 状态: "
                + "IamErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
