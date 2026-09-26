package com.bone.system.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

/** 字典项译文响应（SAP T005T 风格）。 */
@Data
@Builder
public class DictItemTextResp {
  private String typeCode;
  private String code;
  private String language;
  private String label;
  private String description;
}
