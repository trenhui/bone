package com.bone.procurement.fulfillment.domain.model.ack;

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

/** PO 行级确认（855 类比）。幂等键 (tenant_id, po_no, po_version, vendor_party_id)。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_po_ack")
public class PoAcknowledgement extends AbstractEntity<Long> {

  public enum AckType {
    FULL,
    PARTIAL,
    REJECT
  }

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "po_no")
  private String poNo;

  @Column(name = "po_version")
  private Integer poVersion;

  @Column(name = "vendor_party_id")
  private String vendorPartyId;

  @Column(name = "ack_type")
  private String ackType;

  @Column(name = "ack_lines_json")
  private String ackLinesJson;

  @Column(name = "ack_at")
  private Date ackAt;

  @Version
  @Column(name = "version")
  private Integer version;
}
