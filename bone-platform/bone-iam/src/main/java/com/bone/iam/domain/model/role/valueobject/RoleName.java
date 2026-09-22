package com.bone.iam.domain.model.role.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class RoleName {
  private final String value;
}
