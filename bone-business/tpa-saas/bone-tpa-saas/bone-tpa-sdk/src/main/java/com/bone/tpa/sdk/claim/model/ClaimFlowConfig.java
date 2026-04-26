package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

@Table("claim_flow_config")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimFlowConfig extends TenantAbstractEntity<Long> {
    /**
     * 业务标识id
     */
    private String bizIdentityCode;
    /**
     * 0 草稿 1 生效 2 不生效
     */
    private Integer status;
    /**
     * 流程控制
     */
    private String flowConfig;
    /**
     * 初审配置
     */
    private String preCheckConfig ;
    /**
     * 录入配置
     */
    private String inputConfig;
    /**
     * 质检配置
     */
    private String qualityConfig;
    /**
     * 审核配置
     */
    private String approveConfig;
    /**
     * 复核配置
     */
    private String approveCheckConfig;

    /**
     * 推送配置
     */
    private String pushConfig;

    public String getBizIdentityCode() {
        return bizIdentityCode;
    }

    public void setBizIdentityCode(String bizIdentityCode) {
        this.bizIdentityCode = bizIdentityCode;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getFlowConfig() {
        return flowConfig;
    }

    public void setFlowConfig(String flowConfig) {
        this.flowConfig = flowConfig;
    }

    public String getPreCheckConfig() {
        return preCheckConfig;
    }

    public void setPreCheckConfig(String preCheckConfig) {
        this.preCheckConfig = preCheckConfig;
    }

    public String getInputConfig() {
        return inputConfig;
    }

    public void setInputConfig(String inputConfig) {
        this.inputConfig = inputConfig;
    }

    public String getQualityConfig() {
        return qualityConfig;
    }

    public void setQualityConfig(String qualityConfig) {
        this.qualityConfig = qualityConfig;
    }

    public String getApproveConfig() {
        return approveConfig;
    }

    public void setApproveConfig(String approveConfig) {
        this.approveConfig = approveConfig;
    }

    public String getApproveCheckConfig() {
        return approveCheckConfig;
    }

    public void setApproveCheckConfig(String approveCheckConfig) {
        this.approveCheckConfig = approveCheckConfig;
    }

    public String getPushConfig() {
        return pushConfig;
    }

    public void setPushConfig(String pushConfig) {
        this.pushConfig = pushConfig;
    }
}
