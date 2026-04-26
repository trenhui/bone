package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

/**
 * ss_claim_track_log DO
 *
 * @author 0
 */
@Table("ss_claim_track_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimTrackLog extends TenantAbstractEntity<Long> {

    /**
     * 关联赔案id
     */
    private Long relatedClaimId;
    /**
     * 关联赔案号
     *
     */
    private String relatedClaimNo;
    /**
     * 当前阶段
     */
    private String stage;
    /**
     * 操作类型
     */
    private String type;
    /**
     * 更新前数据
     */
    private String beforeValue;
    /**
     * 更新后数据
     */
    private String afterValue;
    /**
     * 操作描述
     */
    private String message;
    /**
     * 操作备注
     */
    private String remark;

    /**
     * 操作者
     */
    private String operator;

    private String extraStore;
}
