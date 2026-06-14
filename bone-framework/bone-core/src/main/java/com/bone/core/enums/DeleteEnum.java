package com.bone.core.enums;

public enum DeleteEnum {
  DELETE(1),
  DEFAULT(0);

  private final Integer status;

  DeleteEnum(Integer status) {
    this.status = status;
  }

  public Integer getStatus() {
    return status;
  }
}
