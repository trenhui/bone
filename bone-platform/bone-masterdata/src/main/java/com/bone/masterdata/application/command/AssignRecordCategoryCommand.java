package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 记录归类命令（G4）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRecordCategoryCommand {

  @NotNull(message = "recordId: 记录ID不能为空")
  private Long recordId;

  @NotNull(message = "categoryId: 分类ID不能为空")
  private Long categoryId;
}
