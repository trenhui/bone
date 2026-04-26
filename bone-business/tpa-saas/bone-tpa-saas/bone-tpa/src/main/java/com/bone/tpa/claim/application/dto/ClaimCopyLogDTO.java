package com.bone.tpa.claim.application.dto;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.Data;

import java.util.Date;

/**
 * 赔案复制记录
 */
@Data
public class ClaimCopyLogDTO extends TenantAbstractEntity<ClaimCopyLogDTO,Long> {
    /**
     * 保单号
     */
    private String policyNo;
    /**
     * 原配案批次
     */
    private String oldBatchNo;
    /**
     * 原赔案id
     */
    private Long oldClaimId;
    /**
     * 原赔案号
     */
    private String oldClaimNo;
    /**
     * 原签收时间
     */
    private Date oldSignTime;

    /**
     * 新配案批次
     */
    private String newBatchNo;
    /**
     * 新赔案id
     */
    private Long newClaimId;
    /**
     * 新赔案号
     */
    private String newClaimNo;
    /**
     * 新签收时间
     */
    private Date newSignTime;

    /**
     * 操作人员
     */
    private String operator;
    /**
     * 复制时间
     */
    private Date operateTime;

    /**
     * 备注
     */
    private String remark;
}
