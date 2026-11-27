package com.bone.procurement.business.infrastructure;

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

/** 授标事实：终态只追加不回写，确认人来自登录态。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_award")
public class AwardEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "award_id")
  private String awardId;

  @Column(name = "rfx_no")
  private String rfxNo;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "confirmed_by")
  private String confirmedBy;

  @Column(name = "confirmed_at")
  private Date confirmedAt;

  @Column(name = "reason")
  private String reason;

  @Version
  @Column(name = "version")
  private Integer version;
}
