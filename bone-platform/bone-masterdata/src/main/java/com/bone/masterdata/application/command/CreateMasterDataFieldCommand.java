package com.bone.masterdata.application.command;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMasterDataFieldCommand {
  private Long masterDataEntityId;
  private String name;
  private String code;
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
