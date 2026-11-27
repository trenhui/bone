package com.bone.procurement.business.domain.model.business;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/** 只读比价投影；不可比报价保留原因码但不进入有效候选排序。 */
public record QuotationComparison(
    String quoteId,
    String supplierId,
    BigDecimal amount,
    String currency,
    boolean comparable,
    String reasonCode,
    String aiRecommendation) {

  public static List<QuotationComparison> compare(
      List<Quotation> quotations, String budgetCurrency, Date now) {
    Objects.requireNonNull(quotations, "报价列表");
    Objects.requireNonNull(budgetCurrency, "预算币种");
    Objects.requireNonNull(now, "当前时间");

    List<QuotationComparison> candidates =
        quotations.stream().map(q -> project(q, budgetCurrency, now)).toList();
    List<QuotationComparison> sorted =
        candidates.stream()
            .filter(QuotationComparison::comparable)
            .sorted(Comparator.comparing(QuotationComparison::amount))
            .toList();
    List<QuotationComparison> incomparable =
        candidates.stream().filter(c -> !c.comparable()).toList();
    return java.util.stream.Stream.concat(sorted.stream(), incomparable.stream()).toList();
  }

  private static QuotationComparison project(Quotation q, String budgetCurrency, Date now) {
    if (!budgetCurrency.equalsIgnoreCase(q.currency())) {
      return new QuotationComparison(
          q.quoteId(),
          q.supplierId(),
          q.amount(),
          q.currency(),
          false,
          "CURRENCY_MISMATCH",
          q.aiRecommendation());
    }
    if (!now.before(q.validUntil())) {
      return new QuotationComparison(
          q.quoteId(),
          q.supplierId(),
          q.amount(),
          q.currency(),
          false,
          "EXPIRED",
          q.aiRecommendation());
    }
    return new QuotationComparison(
        q.quoteId(), q.supplierId(), q.amount(), q.currency(), true, null, q.aiRecommendation());
  }
}
