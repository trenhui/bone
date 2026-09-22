package com.bone.system.domain.model.alert.vo;

public record Threshold(double value) {
  public static Threshold of(double value) {
    return new Threshold(value);
  }
}
