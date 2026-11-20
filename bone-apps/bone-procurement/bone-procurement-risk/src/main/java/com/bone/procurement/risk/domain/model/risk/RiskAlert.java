package com.bone.procurement.risk.domain.model.risk;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 风险预警聚合根（来源幂等：supplier_id + source + source_ref 去重）。
 *
 * <p>AI 仅辅助登记：UNKNOWN/不确定严重度不得自动关闭或自动裁决供应商，一律转人工。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_risk_alert")
public class RiskAlert extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "source")
  private String source;

  @Column(name = "source_ref")
  private String sourceRef;

  @Column(name = "severity")
  private String severity;

  @Column(name = "alert_type")
  private String alertType;

  @Column(name = "status")
  private String status;

  @Column(name = "evidence_ref")
  private String evidenceRef;

  @Column(name = "detected_at")
  private Date detectedAt;

  @Version
  @Column(name = "version")
  private Integer version;

  public static RiskAlert raise(
      Long tenantId,
      String supplierId,
      String source,
      String sourceRef,
      String rawSeverity,
      String alertType,
      String evidenceRef,
      Date detectedAt) {
    RiskAlert a = new RiskAlert();
    a.tenantId = tenantId;
    a.supplierId = supplierId;
    a.source = source;
    a.sourceRef = sourceRef;
    a.severity = Severity.parse(rawSeverity).name();
    a.alertType = alertType;
    a.evidenceRef = evidenceRef;
    a.detectedAt = detectedAt;
    a.status = "OPEN";
    a.version = 0;
    return a;
  }

  public Severity severity() {
    return Severity.parse(severity);
  }

  /** 不确定严重度必须转人工复核，不得自动放行或自动关闭。 */
  public boolean requiresHumanReview() {
    return severity().isIndeterminate();
  }

  public void acknowledgeByHuman(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("预警处置必须具名人工操作人");
    }
    if (!"OPEN".equals(status)) {
      throw new IllegalStateException("只有 OPEN 预警可人工确认");
    }
    this.status = "ACKNOWLEDGED";
  }
}
