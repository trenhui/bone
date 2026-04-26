package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.*;

import java.math.BigDecimal;

/**
 * ss_invoice_project DO
 *
 * @author 0
 */
@Table("ss_invoice_project")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceProject extends ExtraStoreBase<Long> {

    /**
     * 关联发票id
     */
    private Long relatedId;
    /**
     * 项目名称
     */
    private String projectName;

    private String projectCode;
    /**
     * 项目发票金额
     */
    private BigDecimal invoiceAmount;
    /**
     * 项目自费金额
     */
    private BigDecimal projectSelfPayAmount;
    /**
     * 项目部分自费
     */
    private BigDecimal projectPartSelfPayAmount;
    /**
     * 项目统筹金额
     */
    private BigDecimal poolingAmount;
    /**
     * 项目三方支付
     */
    private BigDecimal projectThirdPartyPayAmount;
    /**
     * 合理金额
     */
    private BigDecimal projectReasonableAmount;
    /**
     * 不合理金额
     */
    private BigDecimal projectUnreasonableAmount;



//    private List<InvoiceProjectItem> invoiceProjectItems;
}
