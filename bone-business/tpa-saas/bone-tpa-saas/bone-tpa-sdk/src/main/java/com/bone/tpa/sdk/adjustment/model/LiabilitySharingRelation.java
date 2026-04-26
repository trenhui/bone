package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;
/**
 * 责任共保信息
 *

 */
@Table("ia_liability_sharing_relation")
@Data
public class LiabilitySharingRelation extends TenantAbstractEntity<Long> {

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 责任UUID
     */
    private String liabilityUuid;

    /**
     * 责任共保代码
     */
    private String shareCode;

    /**
     * 计划版本
     */
    private String version;


}
