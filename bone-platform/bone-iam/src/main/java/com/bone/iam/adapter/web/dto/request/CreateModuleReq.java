package com.bone.iam.adapter.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateModuleReq {
  @NotBlank private String name;
  @NotBlank private String code;
  private String description;
}
