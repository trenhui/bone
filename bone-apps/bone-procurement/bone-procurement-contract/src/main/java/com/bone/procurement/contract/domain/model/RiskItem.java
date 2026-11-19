package com.bone.procurement.contract.domain.model;

/**
 * 风险明细项：条款锚点 + 严重度 + 依据 + 置信度。
 *
 * <p>锚点 span 为全文累计 0-based 开区间，条款跨页时 span 覆盖完整条款文本，不因分页截断。
 */
public record RiskItem(
    String clauseNo,
    int page,
    int spanStart,
    int spanEnd,
    RiskLevel severity,
    String basis,
    double confidence) {

  public RiskItem {
    if (spanEnd < spanStart) {
      throw new IllegalArgumentException("spanEnd 必须不小于 spanStart");
    }
  }
}
