package com.bone.metadata.sdk.test.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserRoleDTO {
  private Long id;
  private String name;
  private String role;
}
