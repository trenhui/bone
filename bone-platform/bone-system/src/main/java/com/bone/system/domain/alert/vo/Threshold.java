package com.bone.system.domain.alert.vo;

public record Threshold(double value) {
  public static Threshold of(double value) {
    return new Threshold(value);
  }
}
