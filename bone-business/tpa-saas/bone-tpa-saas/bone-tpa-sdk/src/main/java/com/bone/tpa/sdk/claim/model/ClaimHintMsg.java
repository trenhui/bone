package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

//ss_claim_hint_msg
@Table("ss_claim_hint_msg")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimHintMsg extends TenantAbstractEntity<ClaimHintMsg,Long> {
    /**
     * 关联对象id
     *
     */
    private Long relationId;

    private Long claimNumber;
    /**
     * 类型
     */
    private String hintType;

    /**
     * 提示数据data
     * key - msg 的格式
     */
    private String data;
}
