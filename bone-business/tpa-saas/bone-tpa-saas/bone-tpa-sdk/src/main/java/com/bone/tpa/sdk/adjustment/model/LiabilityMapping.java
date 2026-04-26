package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;

/**
 * 普康责任与保司责任映射关系表
 */
@Table("ia_liability_mapping")
@Data
public class LiabilityMapping extends TenantAbstractEntity<Long> {

    /**
     * 保单号
     */
    private String policyNo;

    /**
     * 计划uuid
     */
    private String planUuid;

    /**
     * 计划名(冗余)
     */
    private String planName;

    /**
     * 责任uuid
     */
    private String liabilityUuid;

    /**
     * 责任名称(冗余)
     */
    private String liabilityName;

    /**
     * 发票医疗类型
     */
    private String invoiceMedicalType;

    /**
     * 保司险种代码
     */
    private String insuranceCompanyCoverage;

    /**
     * 保司责任代码
     */
    private String insuranceCompanyLiability;

    /**
     * 保司责任子码
     */
    private String insuranceCompanyLiabilitySub;

    /**
     * 索赔事故性质
     */
    private String claimAccident;

    /**
     * 保司个账公账
     */
    private String insuranceCompanyAccount;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 更新人
     */
    private String updateUser;
}
