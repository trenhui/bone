package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.*;

import java.math.BigDecimal;

/**
 * ss_invoice_project_item DO
 *
 * @author 0
 */
@Table("ss_invoice_project_item")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceProjectItem extends ExtraStoreBase<Long> {

    /**
     * 关联发票id
     */
    private Long relatedId;

    /**
     * 唯一的uuid
     * 双向生成
     */
    private String itemUuid;

    /**
     * 关联票据号码
     */
    private String relatedInvoiceNo;

    /**
     * 关联项目code
     */
    private String relatedProjectCode;

    /**
     * 关联项目名称
     */
    private String relatedProjectName;

    /**
     * 药品诊疗名称
     */
    private String itemName;

    /**
     * 药品诊疗代码
     */
    private String itemCode;

    /**
     * 类型
     */
    private String type;

    /**
     * 医保费用类型，甲类乙类code
     */
    private String medicalType;

    /**
     * 自费比例
     */
    private BigDecimal chargingPercentage;

    /**
     * 单价
     */
    private BigDecimal price;

    /**
     * 数量
     */
    private Integer count;

    /**
     * 总价
     */
    private BigDecimal itemTotalAmount;

    /**
     * 扣费金额
     */
    private BigDecimal chargingAmount;

    /**
     * 剂型
     */
    private String dosageForm;

    /**
     * 剂型
     */
    private String dosageFormCn;

}
