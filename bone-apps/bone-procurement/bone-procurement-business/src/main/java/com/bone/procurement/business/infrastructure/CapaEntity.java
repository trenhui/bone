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

/** CAPA 持久化事实；关闭证据只存引用，不存 PII 原文。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_capa")
public class CapaEntity extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "capa_no")
  private String capaNo;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "issue_description")
  private String issueDescription;

  @Column(name = "owner_id")
  private String ownerId;

  @Column(name = "due_at")
  private Date dueAt;

  @Column(name = "status")
  private String status;

  @Column(name = "follow_up_required")
  private Boolean followUpRequired;

  @Column(name = "closed_by")
  private String closedBy;

  @Column(name = "closed_at")
  private Date closedAt;

  @Column(name = "evidence_ref")
  private String evidenceRef;

  @Version
  @Column(name = "version")
  private Integer version;
}
