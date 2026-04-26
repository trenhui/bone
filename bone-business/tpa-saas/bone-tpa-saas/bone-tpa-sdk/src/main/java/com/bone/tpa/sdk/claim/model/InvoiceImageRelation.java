package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.*;


/**
 * ss_invoice_image_relation DO
 *
 * @author 0
 */
@Table("ss_invoice_image_relation")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceImageRelation extends AbstractEntity<Long> {

    /**
     * 赔案id
     */
    private Long claimId;

    /**
     * 影像件唯一键,uuid
     */
    private String imageDetailId;

    /**
     * 发票uuid
     */
    private String invoiceUuid;

}
