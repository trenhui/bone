package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

import java.util.Date;

/**
 * 推送b失败操作日志表
 *
 */
@Table("claim_push_fail_operate_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimPushFailOperateLog extends TenantAbstractEntity<ClaimPushFailOperateLog, Long> {

    private Long claimId;

    /**
     * 赔案号
     */
    private String claimNo;

    /**
     * 推送回调原因
     */
    private String pushBackReason;

    /**
     * 错误归档
     */
    private String errorType;

}
