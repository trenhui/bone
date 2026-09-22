package com.bone.iam.domain.model.permission.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class PermissionCode {
  private final String value;
}
