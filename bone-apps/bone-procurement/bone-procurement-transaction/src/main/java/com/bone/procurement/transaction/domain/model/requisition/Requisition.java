package com.bone.procurement.transaction.domain.model.requisition;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 请购单聚合根（持久化由 @Table/@Column 驱动，审计列继承 AbstractEntity / java.util.Date）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_requisition")
public class Requisition extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "pr_no")
  private String prNo;

  @Column(name = "org_id")
  private Long orgId;

  @Column(name = "requester_id")
  private Long requesterId;

  @Column(name = "status")
  private String status;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(name = "currency")
  private String currency;

  @Column(name = "budget_ref")
  private String budgetRef;

  @Version
  @Column(name = "version")
  private Integer version;
}
