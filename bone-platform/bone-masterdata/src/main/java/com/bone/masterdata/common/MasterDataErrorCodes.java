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

  // 容量上限（400）
  public static final String ENTITY_FIELD_LIMIT_EXCEEDED = "MD_ENTITY_FIELD_LIMIT_EXCEEDED";
  public static final String RECORD_SIZE_EXCEEDED = "MD_RECORD_SIZE_EXCEEDED";
  public static final String EXPORT_LIMIT_EXCEEDED = "MD_EXPORT_LIMIT_EXCEEDED";

  // 唯一性冲突（409）
  public static final String FIELD_NAME_DUPLICATE = "MD_FIELD_NAME_DUPLICATE";
  public static final String ENTITY_NAME_DUPLICATE = "MD_ENTITY_NAME_DUPLICATE";
  public static final String ENTITY_CODE_DUPLICATE = "MD_ENTITY_CODE_DUPLICATE";
  public static final String DATA_STANDARD_DUPLICATE = "MD_DATA_STANDARD_DUPLICATE";
  public static final String RULE_NAME_DUPLICATE = "MD_RULE_NAME_DUPLICATE";
  public static final String TEMPLATE_DOMAIN_DUPLICATE = "MD_TEMPLATE_DOMAIN_DUPLICATE";
  public static final String TEMPLATE_VERSION_DUPLICATE = "MD_TEMPLATE_VERSION_DUPLICATE";

  // 域模板（404 / 400）
  public static final String TEMPLATE_NOT_FOUND = "MD_TEMPLATE_NOT_FOUND";
  public static final String TEMPLATE_NOT_PUBLISHED = "MD_TEMPLATE_NOT_PUBLISHED";
  public static final String TEMPLATE_FIELD_SCHEMA_INVALID = "MD_TEMPLATE_FIELD_SCHEMA_INVALID";

  // 数据质量规则（400）
  public static final String RULE_SEVERITY_INVALID = "MD_RULE_SEVERITY_INVALID";
  public static final String RULE_EXPRESSION_INVALID = "MD_RULE_EXPRESSION_INVALID";

  // 记录数据解析（500）
  public static final String RECORD_DATA_PARSE_FAILED = "MD_RECORD_DATA_PARSE_FAILED";

  // 数据标准（404）
  public static final String DATA_STANDARD_NOT_FOUND = "MD_DATA_STANDARD_NOT_FOUND";

  // 文件上传（400）
  public static final String FILE_EMPTY = "MD_FILE_EMPTY";
  public static final String FILE_FORMAT_INVALID = "MD_FILE_FORMAT_INVALID";
  public static final String FILE_READ_FAILED = "MD_FILE_READ_FAILED";
  public static final String FILE_PARSE_FAILED = "MD_FILE_PARSE_FAILED";

  // 审批与 SoD（G5）
  public static final String USER_CONTEXT_REQUIRED = "MD_USER_CONTEXT_REQUIRED";
  public static final String SOD_VIOLATION = "MD_SOD_VIOLATION";
  public static final String WORKFLOW_APPROVAL_REQUIRED = "MD_WORKFLOW_APPROVAL_REQUIRED";

  // 分类体系（G4）
  public static final String CATEGORY_NOT_FOUND = "MD_CATEGORY_NOT_FOUND";
  public static final String CATEGORY_CODE_DUPLICATE = "MD_CATEGORY_CODE_DUPLICATE";
  public static final String CATEGORY_STATE_INVALID = "MD_CATEGORY_STATE_INVALID";

  // 消费订阅（G10）
  public static final String SUBSCRIPTION_DUPLICATE = "MD_SUBSCRIPTION_DUPLICATE";
  public static final String SUBSCRIPTION_STATE_INVALID = "MD_SUBSCRIPTION_STATE_INVALID";

  // 质量整改工单（G11）
  public static final String QUALITY_ISSUE_NOT_FOUND = "MD_QUALITY_ISSUE_NOT_FOUND";

  // 参考数据（G15；2026-09-26 overlay 拆分裁决，§8 约束 6）
  public static final String REF_SET_DUPLICATE = "MD_REF_SET_DUPLICATE";
  public static final String REF_SET_NOT_FOUND = "MD_REF_SET_NOT_FOUND";
  public static final String REF_VALUE_DUPLICATE = "MD_REF_VALUE_DUPLICATE";

  /** 租户尝试写平台目录（建/改/归档值域）——值域是平台域 artifact。 */
  public static final String REF_PLATFORM_SET_IMMUTABLE = "MD_REF_PLATFORM_SET_IMMUTABLE";

  /** 租户尝试修改/停用平台值——租户只能操作自己的私有扩展值。 */
  public static final String REF_PLATFORM_VALUE_IMMUTABLE = "MD_REF_PLATFORM_VALUE_IMMUTABLE";

  // 模型漂移（G16）
  public static final String MODEL_DRIFT_NOT_FOUND = "MD_MODEL_DRIFT_NOT_FOUND";
  public static final String MODEL_DRIFT_NO_SOURCE = "MD_MODEL_DRIFT_NO_SOURCE";

  // 下游反馈（G17）
  public static final String FEEDBACK_NOT_FOUND = "MD_FEEDBACK_NOT_FOUND";

  // 治理角色（G3）
  public static final String STEWARD_DUPLICATE = "MD_STEWARD_DUPLICATE";
  public static final String STEWARD_ROLE_INVALID = "MD_STEWARD_ROLE_INVALID";

  private MasterDataErrorCodes() {}
}
