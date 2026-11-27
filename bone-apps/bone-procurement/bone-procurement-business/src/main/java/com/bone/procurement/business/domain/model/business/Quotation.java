package com.bone.procurement.business.domain.model.business;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

/** 报价事实；AI 评分与推荐只作为建议字段，不产生授标或淘汰结论。 */
public record Quotation(
    Long tenantId,
    String quoteId,
    String rfxNo,
    String supplierId,
    BigDecimal amount,
    String currency,
    Date validUntil,
    BigDecimal aiScore,
    String aiRecommendation) {

  public Quotation {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
    for (String required : new String[] {quoteId, rfxNo, supplierId, currency}) {
      if (required == null || required.isBlank()) {
        throw new IllegalArgumentException("报价必备字段不得为空");
      }
    }
    Objects.requireNonNull(amount, "报价金额");
    Objects.requireNonNull(validUntil, "报价有效期");
  }
}
