package com.bone.masterdata.adapter.web.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 主数据字段创建请求。name/code/type 在 md_field 上为 NOT NULL，缺校验会撞库并被兜成 500。 */
@Data
public class CreateMasterDataFieldReq {
  private Long masterDataEntityId;

  @NotBlank(message = "name: 字段名称不能为空")
  private String name;

  @NotBlank(message = "code: 字段编码不能为空")
  private String code;

  @JsonAlias("fieldType")
  @NotBlank(message = "type: 字段类型不能为空")
  private String type;

  private Integer length;
  private Boolean required;
  private String defaultValue;
  private String description;
  private Integer sortOrder;
}
