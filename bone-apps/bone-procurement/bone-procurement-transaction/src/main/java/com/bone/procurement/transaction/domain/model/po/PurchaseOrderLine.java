package com.bone.procurement.transaction.domain.model.po;

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

/** 采购订单行（逐行净价快照，先舍入再汇总）。 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_po_line")
public class PurchaseOrderLine extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "po_id")
  private Long poId;

  @Column(name = "line_no")
  private Integer lineNo;

  @Column(name = "item_id")
  private String itemId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "quantity")
  private BigDecimal quantity;

  @Column(name = "unit")
  private String unit;

  @Column(name = "net_price")
  private BigDecimal netPrice;

  @Column(name = "tax_rate")
  private BigDecimal taxRate;

  @Column(name = "line_amount")
  private BigDecimal lineAmount;

  @Column(name = "currency")
  private String currency;

  @Version
  @Column(name = "version")
  private Integer version;
}
