package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 责任共保信息
 *

 */
@Table("ia_liability_sharing")
@Data
public class LiabilitySharing extends TenantAbstractEntity<Long> {

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 责任共保ID
     */
    private String shareId;

    /**
     * 责任共保代码
     */
    private String shareCode;

    /**
     * 责任共保额度
     */
    private BigDecimal shareLimit;

    /**
     * 计划版本
     */
    private String version;


}
