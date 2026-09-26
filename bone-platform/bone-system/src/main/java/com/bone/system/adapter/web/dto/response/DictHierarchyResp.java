package com.bone.system.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

/** 层级关系响应（哪套视图、谁挂谁）。 */
@Data
@Builder
public class DictHierarchyResp {
  private String typeCode;
  private String hierarchyCode;
  private String code;
  private String parentCode;
  private Integer sort;
}
