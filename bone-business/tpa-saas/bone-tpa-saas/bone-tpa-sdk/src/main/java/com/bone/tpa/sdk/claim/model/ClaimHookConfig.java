package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Table("claim_hook_config")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimHookConfig  extends TenantAbstractEntity<Long> {
    @Schema(description = "业务身份code")
    private String bizIdentityCode;

    private String hookConfig;

    public String getBizIdentityCode() {
        return bizIdentityCode;
    }

    public void setBizIdentityCode(String bizIdentityCode) {
        this.bizIdentityCode = bizIdentityCode;
    }

    public String getHookConfig() {
        return hookConfig;
    }

    public void setHookConfig(String hookConfig) {
        this.hookConfig = hookConfig;
    }
}
