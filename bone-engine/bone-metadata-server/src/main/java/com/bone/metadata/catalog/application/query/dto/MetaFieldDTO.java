package com.bone.metadata.catalog.application.query.dto;

import lombok.Data;

@Data
public class MetaFieldDTO {
  private Long id;
  private Long entityId;
  private String name;
  private String code;
  private String displayName;
  private String type;
  private Integer length;
  private Boolean required;
  private Boolean unique;
  private Integer sortOrder;

  /** 字段注释 */
  private String comment;

  // ===== 业界元数据 / 数据治理属性 =====
  /** 数据分级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET */
  private String dataClassification;

  /** 是否个人敏感信息(PII) */
  private Boolean pii;

  /** 敏感级别：L1/L2/L3/L4 */
  private String sensitivityLevel;

  /** 数据管家/责任人 */
  private String dataSteward;

  /** 业务术语/数据标准 */
  private String businessTerm;

  /** 来源系统（血缘） */
  private String sourceSystem;

  /** 枚举值/标准码表（JSON 文本） */
  private String enumValues;

  /**
   * 枚举选项源声明（JSON 文本；为空 = inline 本地枚举）。
   *
   * <p>形如 {@code
   * {"type":"dict","ref":"gender","resolvedAt":...,"resolvedCount":...,"resolvedBy":...}}； {@code
   * resolved*} 为服务端物化元数据（建模者只填 type/ref）。设计见 doc/design/modules/11 §4.2。
   */
  private String enumSource;

  /** 校验规则/质量规则（JSON 文本） */
  private String validationRules;

  /** 创建时间 */
  private java.util.Date createdAt;

  /** 乐观锁版本（PUT 使用 If-Match: "v{version}"） */
  private Integer version;
}
