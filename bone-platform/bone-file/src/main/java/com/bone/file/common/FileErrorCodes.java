package com.bone.file.common;

/**
 * bone-file 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} §6）。
 *
 * <p><b>为何需要这一层</b>：此前 5 处失败都以 {@code new IllegalStateException("文件上传失败: ...")} 抛出， 全部被 {@code
 * GlobalExceptionHandler} 兜底成 HTTP 500 + {@code INTERNAL_ERROR}，前端无法按码提示。 稳定码是跨系统契约，中文说明只是
 * fallback（错误码登记 §2「可聚合」「可 i18n」）。
 *
 * <p><b>用法</b>：抛出走 {@link FileErrors#of(String, Object)}，不要在抛出点手写 HTTP 状态—— 状态与码的配对真源在 {@link
 * FileErrors}，两处各写一遍必然漂移。
 *
 * <p><b>命名</b>：{@code FILE_} 为文件模块前缀，格式 {@code FILE_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class FileErrorCodes {

  /** 上传失败（存储不可用 / 写入中断）。 */
  public static final String UPLOAD_FAILED = "FILE_UPLOAD_FAILED";

  /** 下载失败（对象不可读 / 存储异常）。 */
  public static final String DOWNLOAD_FAILED = "FILE_DOWNLOAD_FAILED";

  /** 删除失败。 */
  public static final String DELETE_FAILED = "FILE_DELETE_FAILED";

  /** 对象不存在。 */
  public static final String NOT_FOUND = "FILE_NOT_FOUND";

  /** 对象名非法：含路径遍历片段、绝对路径或控制字符。 */
  public static final String NAME_INVALID = "FILE_NAME_INVALID";

  /** 文件扩展名不在白名单内。 */
  public static final String TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";

  /** 缺少租户上下文，无法判定对象归属（失败关闭）。 */
  public static final String TENANT_CONTEXT_MISSING = "FILE_TENANT_CONTEXT_MISSING";

  /** 跨租户访问被拒绝：对象不属于当前调用方租户。 */
  public static final String ACCESS_DENIED = "FILE_ACCESS_DENIED";

  private FileErrorCodes() {}
}
