package com.bone.masterdata.common;

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
 * X + ": " + id)}，状态与码的配对就散落在全模块数十处，任一处不一致都没有机制发现。
 *
 * <p><b>本类的做法</b>：把配对收进 {@link #DEFAULT_HTTP_STATUS} 一张表，抛出点只表达<strong>业务语义</strong>（哪个码 +
 * 什么上下文）。表与《Bone-错误码登记》§6 的 {@code MD_} 表逐行对应，是代码侧的唯一副本。
 *
 * <p><b>新增错误码的强制约束</b>：{@link MasterDataErrorCodes} 的每个 public String 常量都必须在表中登记状态，否则类加载即抛 {@link
 * IllegalStateException}——漏登记不可能溜到运行期（fail fast，而不是让异常被兜底成 400/500）。
 */
public final class MasterDataErrors {

  /**
   * 码 → 默认 HTTP 状态（真源）。
   *
   * <p>与《Bone-错误码登记》§6 {@code MD_} 表一一对应；{@link #checkEveryCodeRegistered()} 保证不会漏。
   */
  private static final Map<String, Integer> DEFAULT_HTTP_STATUS =
      Map.ofEntries(
          // 元数据实体
          Map.entry(MasterDataErrorCodes.META_ENTITY_NOT_FOUND, 404),
          Map.entry(MasterDataErrorCodes.META_ENTITY_NOT_PUBLISHED, 422),
          // 参数 / 必填
          Map.entry(MasterDataErrorCodes.ENTITY_ID_REQUIRED, 400),
          // 导出
          Map.entry(MasterDataErrorCodes.EXPORT_SERIALIZE_FAILED, 500),
          // 唯一性冲突（409）
          Map.entry(MasterDataErrorCodes.FIELD_NAME_DUPLICATE, 409),
          Map.entry(MasterDataErrorCodes.ENTITY_NAME_DUPLICATE, 409),
          Map.entry(MasterDataErrorCodes.DATA_STANDARD_DUPLICATE, 409),
          Map.entry(MasterDataErrorCodes.RULE_NAME_DUPLICATE, 409),
          // 数据标准（404）
          Map.entry(MasterDataErrorCodes.DATA_STANDARD_NOT_FOUND, 404),
          // 文件上传（400）
          Map.entry(MasterDataErrorCodes.FILE_EMPTY, 400),
          Map.entry(MasterDataErrorCodes.FILE_FORMAT_INVALID, 400),
          Map.entry(MasterDataErrorCodes.FILE_READ_FAILED, 400),
          Map.entry(MasterDataErrorCodes.FILE_PARSE_FAILED, 400));

  static {
    checkEveryCodeRegistered();
  }

  private MasterDataErrors() {}

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
              + "；请在 MasterDataErrors 的 DEFAULT_HTTP_STATUS 中登记，"
              + "否则该异常会被 GlobalExceptionHandler 兜底成 400。");
    }
    return status;
  }

  private static String composeMessage(String errorCode, Object detail) {
    return detail == null ? errorCode : errorCode + ": " + detail;
  }

  /**
   * 校验 {@link MasterDataErrorCodes} 的每个码常量都在状态表中。
   *
   * <p>用反射而非人工清单：人工清单本身就是又一份需要同步的数据，与「消除配对漂移」的初衷相悖。
   */
  private static void checkEveryCodeRegistered() {
    for (Field field : MasterDataErrorCodes.class.getDeclaredFields()) {
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
            "错误码常量未在 MasterDataErrors 登记默认 HTTP 状态: "
                + "MasterDataErrorCodes."
                + field.getName()
                + " = \""
                + code
                + "\"。新增错误码必须同步登记状态（错误码登记 §7）。");
      }
    }
  }
}
