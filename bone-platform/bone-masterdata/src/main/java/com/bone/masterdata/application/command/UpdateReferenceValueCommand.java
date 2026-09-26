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
public class UpdateReferenceValueCommand {

  @NotNull(message = "id: 值ID不能为空")
  private Long id;

  private String valueName;
  private String externalCode;
  private Integer sortOrder;
}
