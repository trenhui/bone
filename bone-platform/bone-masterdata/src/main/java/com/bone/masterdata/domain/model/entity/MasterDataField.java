package com.bone.masterdata.domain.model.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.masterdata.domain.model.field.valueobject.FieldCode;
import com.bone.masterdata.domain.model.field.valueobject.FieldName;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("mdm_field")
public class MasterDataField extends TenantAbstractEntity<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long masterDataEntityId;
  private FieldName name;
  private FieldCode code;
  private String type;
  private Integer length;
  private Boolean required;
  private String defaultValue;
  private String description;
  private Integer sortOrder;

  public static MasterDataField create(
      Long id,
      Long masterDataEntityId,
      FieldName name,
      FieldCode code,
      String type,
      Integer length,
      Boolean required,
      String defaultValue,
      String description,
      Integer sortOrder) {
    MasterDataField field = new MasterDataField();
    field.id = id;
    field.masterDataEntityId = masterDataEntityId;
    field.name = name;
    field.code = code;
    field.type = type;
    field.length = length;
    field.required = required;
    field.defaultValue = defaultValue;
    field.description = description;
    field.sortOrder = sortOrder;
    field.setCreatedAt(new java.util.Date());
    field.setUpdatedAt(new java.util.Date());
    return field;
  }

  /**
   * 校验单个字段值是否满足本字段定义。
   *
   * <p>真实场景：字段定义（必填 / 类型 / 长度）必须在记录写入时生效，否则建模只是展示用的 "死定义"，主数据治理无从谈起。校验规则内聚在字段自身，保证领域行为归属正确。
   *
   * @param value 字段值（取自记录 data JSON，已按 field code 取值）
   * @return 违规描述；{@code null} 表示通过
   */
  public String validateValue(Object value) {
    String label = code != null ? code.value() : (name != null ? name.value() : "?");
    String text = value == null ? null : String.valueOf(value);
    boolean blank = text == null || text.isBlank();
    if (Boolean.TRUE.equals(required) && blank) {
      return "字段[" + label + "]为必填项";
    }
    if (blank) {
      return null;
    }
    String trimmed = text.trim();
    String typeName = type == null ? "STRING" : type.trim().toUpperCase(Locale.ROOT);
    switch (typeName) {
      case "NUMBER" -> {
        try {
          new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
          return "字段[" + label + "]不是合法数值：" + trimmed;
        }
      }
      case "BOOLEAN" -> {
        if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
          return "字段[" + label + "]不是合法布尔值（true/false）：" + trimmed;
        }
      }
      case "DATE" -> {
        if (!isParseableDateTime(trimmed)) {
          return "字段[" + label + "]不是合法日期：" + trimmed;
        }
      }
      default -> {
        // STRING / TEXT 及未知类型：不做类型强校验，仅受长度约束
      }
    }
    if (length != null && length > 0 && text.length() > length) {
      return "字段[" + label + "]长度 " + text.length() + " 超过上限 " + length;
    }
    return null;
  }

  private static boolean isParseableDateTime(String text) {
    try {
      LocalDate.parse(text);
      return true;
    } catch (DateTimeParseException e) {
      try {
        LocalDateTime.parse(text);
        return true;
      } catch (DateTimeParseException inner) {
        return false;
      }
    }
  }

  public void update(
      FieldName name,
      String type,
      Integer length,
      Boolean required,
      String defaultValue,
      String description,
      Integer sortOrder) {
    this.name = name;
    this.type = type;
    this.length = length;
    this.required = required;
    this.defaultValue = defaultValue;
    this.description = description;
    this.sortOrder = sortOrder;
    this.setUpdatedAt(new java.util.Date());
  }
}
