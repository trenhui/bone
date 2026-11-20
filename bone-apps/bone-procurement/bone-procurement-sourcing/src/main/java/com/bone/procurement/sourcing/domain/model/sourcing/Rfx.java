package com.bone.procurement.sourcing.domain.model.sourcing;

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

/** RFx 聚合根（INV-03 截止时间单调、到期后禁止收标；开标前密封标志恒真）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_rfx")
public class Rfx extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "rfx_no")
  private String rfxNo;

  @Column(name = "rfx_type")
  private String rfxType;

  @Column(name = "publish_at")
  private Date publishAt;

  @Column(name = "due_at")
  private Date dueAt;

  @Column(name = "status")
  private String status;

  @Column(name = "sealed")
  private Boolean sealed;

  @Version
  @Column(name = "version")
  private Integer version;

  public static Rfx create(
      Long tenantId, Long projectId, String rfxNo, String rfxType, Date dueAt) {
    Rfx r = new Rfx();
    r.tenantId = tenantId;
    r.projectId = projectId;
    r.rfxNo = rfxNo;
    r.rfxType = rfxType;
    r.dueAt = dueAt;
    r.status = "DRAFT";
    r.sealed = true;
    r.version = 0;
    return r;
  }

  public boolean acceptsBidAt(Date now) {
    return "PUBLISHED".equals(status) && now != null && dueAt != null && now.before(dueAt);
  }

  /** 截止时间单调：只允许向后顺延，不允许提前（守住已发出的截止承诺）。 */
  public void extendDueAt(Date newDueAt) {
    if (newDueAt == null || dueAt != null && !newDueAt.after(dueAt)) {
      throw new IllegalArgumentException("截止时间必须单调向后（INV-03）");
    }
    this.dueAt = newDueAt;
  }

  public void publish(Date at) {
    if (!"DRAFT".equals(status)) {
      throw new IllegalStateException("只有 DRAFT 的 RFx 可发布");
    }
    if (at != null && dueAt != null && !at.before(dueAt)) {
      throw new IllegalArgumentException("发布时间必须早于截止时间");
    }
    this.status = "PUBLISHED";
    this.publishAt = at;
  }
}
