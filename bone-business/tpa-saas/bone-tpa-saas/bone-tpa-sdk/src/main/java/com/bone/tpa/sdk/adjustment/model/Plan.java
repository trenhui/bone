package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;

import java.math.BigDecimal;
/**
 * 保险计划信息
 *

 */
@Table("ia_plan")
@Data
public class Plan extends TenantAbstractEntity<Long> {


    private String uuid;

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 计划代码
     */
    private String planCode;

    /**
     * 排序字段
     */
    private Long sortNumber;

    /**
     * 计划额度
     */
    private BigDecimal planLimit;

    /**
     * 计划版本
     */
    private String version;

    /**
     * 计划版本状态
     */
    private String status;


}
