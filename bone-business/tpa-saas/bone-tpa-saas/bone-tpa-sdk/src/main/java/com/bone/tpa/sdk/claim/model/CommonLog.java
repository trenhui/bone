package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

@Table("claim_command_log")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class CommonLog  extends TenantAbstractEntity<Long> {

    private String objectId ;

    private String bizType;

    private String remark ;

    private String traceId ;

    private String company;
}
