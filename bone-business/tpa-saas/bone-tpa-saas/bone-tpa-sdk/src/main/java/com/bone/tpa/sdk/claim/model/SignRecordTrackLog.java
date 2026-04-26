package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

/**
 * ss_sign_record_track_log DO
 *
 * @author 0
 */
@Table("ss_sign_record_track_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SignRecordTrackLog extends TenantAbstractEntity<Long> {

    /**
     * 关联签收记录id
     */
    private Long relatedSignRecordId;
    /**
     * 签收状态
     */
    private String signStatus;
    /**
     * 操作类型
     */
    private String type;
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

}
