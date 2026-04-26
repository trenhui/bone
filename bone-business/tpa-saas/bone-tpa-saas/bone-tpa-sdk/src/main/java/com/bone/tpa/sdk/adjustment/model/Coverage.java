package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;

import java.math.BigDecimal;
/**
 * 保险险种信息
 *

 */
@Table("ia_coverage")
@Data
public class Coverage extends TenantAbstractEntity<Long> {

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 险种名称
     */
    private String coverageName;

    /**
     * 险种代码
     */
    private String coverageCode;

    /**
     * 险种额度
     */
    private BigDecimal coverageLimit;

    /**
     * 计划版本
     */
    private String version;


}
