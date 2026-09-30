package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMasterDataEntityCommand {

  /** md_entity.name 为 NOT NULL：入口不校验会撞 DB 约束并被兜成 500。 */
  @NotBlank(message = "name: 实体名称不能为空")
  private String name;

  private String description;
  private String category;

  /** 实体编码（下游按编码定位主数据实体；可空=未登记编码）。 */
  private String entityCode;
}
