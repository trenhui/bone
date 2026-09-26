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
public class CreateReferenceSetCommand {

  @NotBlank(message = "setCode: 值域编码不能为空")
  private String setCode;

  @NotBlank(message = "setName: 值域名称不能为空")
  private String setName;

  private String externalStandard;
  private String description;
}
