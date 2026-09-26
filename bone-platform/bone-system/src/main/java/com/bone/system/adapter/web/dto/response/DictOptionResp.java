package com.bone.system.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

/** 下拉数据源响应（平台消费字典的统一形状）。 */
@Data
@Builder
public class DictOptionResp {
  private String code;
  private String label;
  private String value;
  private String tagType;
  private boolean isDefault;
  private Integer sort;
}
