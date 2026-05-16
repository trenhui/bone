package com.bone.metadata.engine.metadata;

/** 元数据未找到异常类 */
public class MetadataNotFoundException extends RuntimeException {

  public MetadataNotFoundException(String message) {
    super(message);
  }

  public MetadataNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
