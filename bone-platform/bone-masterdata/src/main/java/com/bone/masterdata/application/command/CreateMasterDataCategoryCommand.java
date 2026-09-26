package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMasterDataCategoryCommand {

  @NotNull(message = "masterDataEntityId: 主数据实体ID不能为空")
  private Long masterDataEntityId;

  @NotBlank(message = "code: 分类编码不能为空")
  private String code;

  @NotBlank(message = "name: 分类名称不能为空")
  private String name;

  private String description;

  /** 父分类ID；空表示根节点。 */
  private Long parentCategoryId;

  private Integer sortOrder;
}
