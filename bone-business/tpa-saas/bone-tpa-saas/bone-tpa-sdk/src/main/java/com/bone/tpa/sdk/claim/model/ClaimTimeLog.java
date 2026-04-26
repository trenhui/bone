package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.*;

import java.util.Date;

/**
 * ss_claim_time_log DO
 *
 * @author 0
 */
@Table("ss_claim_time_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimTimeLog extends AbstractEntity<Long> {

    /**
     * 关联赔案id
     */
    private String claimId;

    /**
     * 当前阶段
     */
    private String claimStage;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作时间
     */
    private Date operationTime;

    /**
     * 操作者
     */
    private String operator;
}
