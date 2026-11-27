package com.bone.procurement.business.domain.model.business;

import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 授标事实：只能由登录态具名人工产生，且写入后为终态。 */
public final class Award {

  private static final Set<String> FORBIDDEN_ACTORS =
      Set.of("AI", "AI_SUGGESTION", "SYSTEM", "AUTO", "AUTOMATION");

  private final Long tenantId;
  private final String rfxNo;
  private final String supplierId;
  private final String confirmedBy;
  private final Date confirmedAt;
  private final String reason;

  private Award(
      Long tenantId,
      String rfxNo,
      String supplierId,
      String confirmedBy,
      Date confirmedAt,
      String reason) {
    this.tenantId = tenantId;
    this.rfxNo = rfxNo;
    this.supplierId = supplierId;
    this.confirmedBy = confirmedBy;
    this.confirmedAt = confirmedAt;
    this.reason = reason;
  }

  public static Award decide(
      Long tenantId, String rfxNo, String supplierId, String operator, String reason, Date now) {
    if (tenantId == null || tenantId <= 0) {
      throw new IllegalArgumentException("缺失登录态租户，fail-closed");
    }
    requireText(rfxNo, "RFx编号");
    requireText(supplierId, "中标供应商");
    requireText(reason, "授标理由");
    requireHuman(operator);
    return new Award(
        tenantId,
        rfxNo.trim(),
        supplierId.trim(),
        operator.trim(),
        new Date(Objects.requireNonNull(now, "确认时间").getTime()),
        reason.trim());
  }

  public void assertNotRewritable() {
    throw new IllegalStateException("授标为终态，禁止重复或回写");
  }

  public Long tenantId() {
    return tenantId;
  }

  public String rfxNo() {
    return rfxNo;
  }

  public String supplierId() {
    return supplierId;
  }

  public String confirmedBy() {
    return confirmedBy;
  }

  public Date confirmedAt() {
    return new Date(confirmedAt.getTime());
  }

  public String reason() {
    return reason;
  }

  public static void requireHuman(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("人工确认人不得为空");
    }
    String normalized = operator.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if (FORBIDDEN_ACTORS.contains(normalized) || normalized.startsWith("AI_")) {
      throw new IllegalArgumentException("AI 建议或系统身份不得直接人工授标");
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不得为空");
    }
  }
}
