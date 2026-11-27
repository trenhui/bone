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

/** 投标持久化事实；业务唯一键由 DDL 与 Adapter 双层守护。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_bid")
public class BidEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "bid_id")
  private String bidId;

  @Column(name = "rfx_no")
  private String rfxNo;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "source_ref")
  private String sourceRef;

  @Column(name = "due_at")
  private Date dueAt;

  @Column(name = "status")
  private String status;

  @Version
  @Column(name = "version")
  private Integer version;
}
