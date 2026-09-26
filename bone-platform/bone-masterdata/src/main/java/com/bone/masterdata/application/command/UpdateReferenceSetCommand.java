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
public class UpdateReferenceSetCommand {

  @NotNull(message = "id: 值域ID不能为空")
  private Long id;

  private String setName;
  private String externalStandard;
  private String description;
}
