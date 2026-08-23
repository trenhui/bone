package com.bone.gateway.dto;

/** 网关错误响应体。 */
public record ErrorBody(int code, String message) {
  public static ErrorBody of(int code, String message) {
    return new ErrorBody(code, message);
  }
}
