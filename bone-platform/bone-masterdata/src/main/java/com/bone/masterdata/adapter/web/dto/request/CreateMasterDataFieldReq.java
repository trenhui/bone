package com.bone.masterdata.adapter.web.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class CreateMasterDataFieldReq {
  private Long masterDataEntityId;
  private String name;
  private String code;

  @JsonAlias("fieldType")
  private String type;

  private Integer length;
  private Boolean required;
  private String defaultValue;
  private String description;
  private Integer sortOrder;
}
