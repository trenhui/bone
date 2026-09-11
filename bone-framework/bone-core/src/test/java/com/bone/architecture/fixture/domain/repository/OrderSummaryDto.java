package com.bone.architecture.fixture.domain.repository;

/** 规则单测夹具：读侧投影 DTO（写侧仓储不得返回）。 */
public class OrderSummaryDto {
  private final Long id;
  private final String status;

  public OrderSummaryDto(Long id, String status) {
    this.id = id;
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }
}
