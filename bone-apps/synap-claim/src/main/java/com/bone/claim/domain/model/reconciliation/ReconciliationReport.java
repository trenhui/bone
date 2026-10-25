package com.bone.claim.domain.model.reconciliation;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;

/**
 * 资金对账报告（append-only，日终，§11.7）。
 *
 * <p>同一 {@code (tenant_id, biz_date, channel)} 唯一（§9.5 UK）。append-only：对账结论一经落库不可篡改，
 * 重跑同日对账由应用层跳过（幂等），不靠 UPDATE 覆盖。
 *
 * <p><b>一期通道</b>：{@link #CHANNEL_QUOTA}（额度守恒核对）与 {@link #CHANNEL_PAYMENT}（结算单 vs 资金流水阶段一致性）。
 * 外部支付网关/TPA 对账（{@link #CHANNEL_TPA}）待外部对账文件接入后启用。
 */
@Getter
@Table("t_reconciliation_report")
public class ReconciliationReport extends TenantAbstractEntity<Long> {

  public static final String CHANNEL_QUOTA = "QUOTA";
  public static final String CHANNEL_PAYMENT = "PAYMENT";
  public static final String CHANNEL_TPA = "TPA";

  public static final String STATUS_BALANCED = "BALANCED";
  public static final String STATUS_DIFF = "DIFF";

  @Column(name = "biz_date", nullable = false)
  private LocalDate bizDate;

  @Column(name = "channel", nullable = false)
  private String channel;

  @Column(name = "total_count", nullable = false)
  private Integer totalCount;

  @Column(name = "balanced_count", nullable = false)
  private Integer balancedCount;

  @Column(name = "diff_count", nullable = false)
  private Integer diffCount;

  @Column(name = "total_amount", nullable = false)
  private BigDecimal totalAmount;

  @Column(name = "counter_amount", nullable = false)
  private BigDecimal counterAmount;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  ReconciliationReport() {}

  public static ReconciliationReport create(
      Long tenantId,
      LocalDate bizDate,
      String channel,
      int totalCount,
      int balancedCount,
      int diffCount,
      BigDecimal totalAmount,
      BigDecimal counterAmount,
      Instant checkedAt) {
    ReconciliationReport report = new ReconciliationReport();
    report.setTenantId(tenantId);
    report.bizDate = bizDate;
    report.channel = channel;
    report.totalCount = totalCount;
    report.balancedCount = balancedCount;
    report.diffCount = diffCount;
    report.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    report.counterAmount = counterAmount == null ? BigDecimal.ZERO : counterAmount;
    report.status = diffCount == 0 ? STATUS_BALANCED : STATUS_DIFF;
    report.checkedAt = checkedAt;
    return report;
  }
}
