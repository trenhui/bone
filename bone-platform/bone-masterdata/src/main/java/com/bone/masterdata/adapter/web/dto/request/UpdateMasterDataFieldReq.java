package com.bone.masterdata.adapter.web.dto.request;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateMasterDataFieldReq {
  private String name;
  private String type;
  private Integer length;
  private Boolean required;
  private String defaultValue;
  private String description;
  private Integer sortOrder;

  /** 数值字段取值下限（NUMBER 类型生效）。 */
  private BigDecimal minValue;

  /** 数值字段取值上限（NUMBER 类型生效）。 */
  private BigDecimal maxValue;
}
