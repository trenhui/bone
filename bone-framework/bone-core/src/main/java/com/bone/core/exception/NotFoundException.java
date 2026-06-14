package com.bone.core.exception;

import java.io.Serial;

/** 资源未找到异常 */
public class NotFoundException extends BizException {

  @Serial private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }

  public static NotFoundException of(String message) {
    return new NotFoundException(message);
  }
}
