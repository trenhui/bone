package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
/**
 * 保险责任信息
 *

 */
@Table("ia_liability")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Liability extends TenantAbstractEntity<Long> {

    /**
     * UUID
     */
    private String uuid;

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 险种ID
     */
    private Long coverageId;

    /**
     * 责任编码
     */
    private String liabilityCode;

    /**
     * 责任名称
     */
    private String liabilityName;

    /**
     * 责任类型
     */
    private String liabilityType;

    /**
     * 计划版本
     */
    private String version;

    /**
     * 后付责任UUID
     */
    private String nextLiabilityUuid;

    /**
     * 后付责任类型
     */
    private String nextLiabilityType;

    /**
     * 能否被设置为发票关联责任
     */
    private Boolean invoiceRelateAble = true;

    /**
     * 给付依据（定额给付使用
     */
    private String paymentBasis;

    /**
     * 津贴细则（津贴给付使用
     */
    private String allowanceDetail;

    /**
     * 适用对象
     */
    private String restrictObject;

    /**
     * 适用限定
     */
    private String restrictScope;

    /**
     * 适用出险
     */
    private String restrictOutInsure;

    /**
     * 等待期
     */
    private Integer waitingPeriod;

    /**
     * 赔付比例
     */
    private String payPercent;

    /**
     * 责任免赔
     */
    private String liabilityDeduct;

    /**
     * 次日限额
     */
    private String timesLimit;

    /**
     * 责任账户类型
     */
    private String accountType;

    /**
     * 控款方
     */
    private String quotaController;

    /**
     * 保额类型
     */
    private String insuranceQuotaType;

    /**
     * 责任额度
     */
    private String liabilityLimit;

    /**
     * 理算规则
     */
    private String adjustmentDetail;

    /**
     * 理算公式
     */
    private String formula;

    /**
     * 额外记录
     */
    private String remark;


}
