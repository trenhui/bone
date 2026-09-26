package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMasterDataCategoryCommand {

  @NotNull(message = "id: 分类ID不能为空")
  private Long id;

  private String name;
  private String description;
  private Integer sortOrder;
}
