package com.bone.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举 包含HTTP标准状态码和业务状态码
 *
 * @author Bone Framework Team
 * @since 2025
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

  // === 成功状态码 ===
  SUCCESS(200, "操作成功"),

  // === 客户端错误 ===
  BAD_REQUEST(400, "请求参数错误"),
  UNAUTHORIZED(401, "未授权"),
  FORBIDDEN(403, "禁止访问"),
  NOT_FOUND(404, "资源不存在"),

  // === 服务器错误 ===
  SERVER_ERROR(500, "系统内部错误"),

  // === 业务错误 ===
  BUSINESS_ERROR(1000, "业务逻辑错误"),
  VALIDATION_ERROR(1001, "参数校验失败"),
  DATA_ACCESS_ERROR(1002, "数据访问异常");

  private final Integer code;
  private final String message;

  public static ResultCode getValue(int code) {
    for (ResultCode value : values()) {
      if (value.getCode() == code) {
        return value;
      }
    }
    return null;
  }
}
