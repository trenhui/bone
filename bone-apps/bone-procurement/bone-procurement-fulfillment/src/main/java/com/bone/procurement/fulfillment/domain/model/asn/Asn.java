package com.bone.procurement.fulfillment.domain.model.asn;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发货通知聚合根（856 类比）。asn_no 租户内唯一；状态由 AsnStatus 显式迁移表守护。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_asn")
public class Asn extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "asn_no")
  private String asnNo;

  @Column(name = "po_no")
  private String poNo;

  @Column(name = "vendor_party_id")
  private String vendorPartyId;

  @Column(name = "ship_date")
  private java.util.Date shipDate;

  @Column(name = "carrier")
  private String carrier;

  @Column(name = "tracking_no")
  private String trackingNo;

  @Column(name = "sscc_json")
  private String ssccJson;

  @Column(name = "status")
  private String status;

  @Version
  @Column(name = "version")
  private Integer version;
}
