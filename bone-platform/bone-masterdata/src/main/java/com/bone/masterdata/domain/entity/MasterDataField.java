package com.bone.masterdata.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.masterdata.domain.model.field.vo.FieldCode;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("md_field")
public class MasterDataField extends AbstractEntity<Long> {
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
