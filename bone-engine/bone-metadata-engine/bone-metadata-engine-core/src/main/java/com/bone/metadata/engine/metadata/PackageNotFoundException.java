package com.bone.metadata.engine.metadata;

/** 包未找到异常类 */
public class PackageNotFoundException extends RuntimeException {

  public PackageNotFoundException(String message) {
    super(message);
  }

  public PackageNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
