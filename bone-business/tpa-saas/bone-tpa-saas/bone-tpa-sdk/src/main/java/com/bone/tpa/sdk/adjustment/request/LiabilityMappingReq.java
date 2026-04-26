package com.bone.tpa.sdk.adjustment.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiabilityMappingReq {

    @NotNull(message = "数据id不能为空")
    private Long id;

    /**
     * 保司险种代码
     */
    private String insuranceCompanyCoverage;

    /**
     * 保司责任代码
     */
    private String insuranceCompanyLiability;

    /**
     * 保司责任子码
     */
    private String insuranceCompanyLiabilitySub;

    /**
     * 索赔事故性质
     */
    private String claimAccident;

    /**
     * 保司个账公账
     */
    private String insuranceCompanyAccount;
}
