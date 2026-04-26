package com.bone.tpa.claim.application.request;

import com.bone.tpa.sdk.adjustment.response.CertificateConfig;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PolicyCertificateConfig {

    @NotEmpty(message = "保单号不能为空")
    private String policyNo;

    @NotNull(message = "单证配置不能为空")
    private CertificateConfig config;
}
