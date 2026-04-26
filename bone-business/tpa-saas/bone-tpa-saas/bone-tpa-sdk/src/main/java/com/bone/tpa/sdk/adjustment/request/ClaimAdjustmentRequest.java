package com.bone.tpa.sdk.adjustment.request;

import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ClaimAdjustmentRequest {

    @NotNull(message = "赔案Id不能为空")
    private Long claimId;

    @NotNull(message = "赔案号不能为空")
    private String claimNo;

    @NotNull(message = "保单号不能为空")
    private String policyNo;

    @NotNull(message = "计划Id不能为空")
    private Long planId;

    @NotNull(message = "出险人证件号不能为空")
    private String identityNo;

    @NotNull(message = "赔案信息不能为空")
    private Claim claim;

    @NotNull(message = "出险人不能为空")
    private ClaimStakeholder stakeholder;

    @NotEmpty(message = "赔案发票不能为空")
    @Size(min = 1, message = "理赔发票至少有一项")
    private List<ClaimInvoice> claimInvoices;

    @NotEmpty(message = "责任信息不能为空")
    @Size(min = 1, message = "责任信息至少有一项")
    private List<Liability> liabilities;
}
