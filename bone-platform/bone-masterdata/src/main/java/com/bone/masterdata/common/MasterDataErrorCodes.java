package com.bone.masterdata.common;

/** 主数据业务错误码（单一真源）。状态映射见 {@link MasterDataErrors}，登记见 doc/architecture/Bone-错误码登记.md § MD_。 */
public final class MasterDataErrorCodes {

  // 元数据实体
  public static final String META_ENTITY_NOT_FOUND = "MD_META_ENTITY_NOT_FOUND";
  public static final String META_ENTITY_NOT_PUBLISHED = "MD_META_ENTITY_NOT_PUBLISHED";

  // 参数 / 必填
  public static final String ENTITY_ID_REQUIRED = "MD_ENTITY_ID_REQUIRED";

  // 导出
  public static final String EXPORT_SERIALIZE_FAILED = "MD_EXPORT_SERIALIZE_FAILED";

  // 唯一性冲突（409）
  public static final String FIELD_NAME_DUPLICATE = "MD_FIELD_NAME_DUPLICATE";
  public static final String ENTITY_NAME_DUPLICATE = "MD_ENTITY_NAME_DUPLICATE";
  public static final String DATA_STANDARD_DUPLICATE = "MD_DATA_STANDARD_DUPLICATE";

  // 数据标准（404）
  public static final String DATA_STANDARD_NOT_FOUND = "MD_DATA_STANDARD_NOT_FOUND";

  // 文件上传（400）
  public static final String FILE_EMPTY = "MD_FILE_EMPTY";
  public static final String FILE_FORMAT_INVALID = "MD_FILE_FORMAT_INVALID";
  public static final String FILE_READ_FAILED = "MD_FILE_READ_FAILED";
  public static final String FILE_PARSE_FAILED = "MD_FILE_PARSE_FAILED";

  private MasterDataErrorCodes() {}
}
