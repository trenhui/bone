package com.bone.system.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateDictReq {
  private String typeName;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
}
