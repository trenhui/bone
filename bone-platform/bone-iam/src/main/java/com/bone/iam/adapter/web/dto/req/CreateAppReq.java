package com.bone.iam.adapter.web.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAppReq {
  @NotBlank private String name;
  @NotBlank private String code;
  private String description;
  private String icon;
}
