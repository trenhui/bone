package com.bone.procurement.fulfillment.domain.model.asn;

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

/** ASN 明细行（随根一致性）。qty 使用 DECIMAL(19,6)。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_asn_line")
public class AsnLine extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "asn_id")
  private Long asnId;

  @Column(name = "line_no")
  private Integer lineNo;

  @Column(name = "po_line_no")
  private Integer poLineNo;

  @Column(name = "item_id")
  private String itemId;

  @Column(name = "qty")
  private BigDecimal qty;

  @Column(name = "uom")
  private String uom;

  @Column(name = "pack_ref")
  private String packRef;

  @Version
  @Column(name = "version")
  private Integer version;
}
