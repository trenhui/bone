package com.bone.metadata.catalog.common;

/** 元数据模块业务错误码（对齐 Bone-API META_* 前缀）。 */
public final class MetaErrorCodes {

  public static final String VALIDATION_FAILED = "META_VALIDATION_FAILED";
  public static final String BIZ_ERROR = "META_BIZ_ERROR";
  public static final String DOMAIN_ERROR = "META_DOMAIN_ERROR";
  public static final String PRECONDITION_FAILED = "META_PRECONDITION_FAILED";
  public static final String IDEMPOTENCY_CONFLICT = "META_IDEMPOTENCY_CONFLICT";
  public static final String FIELD_CONFLICT = "META_FIELD_CONFLICT";
  public static final String TOO_MANY_REQUESTS = "META_TOO_MANY_REQUESTS";
  public static final String INTERNAL_ERROR = "META_INTERNAL_ERROR";

  private MetaErrorCodes() {}
}
