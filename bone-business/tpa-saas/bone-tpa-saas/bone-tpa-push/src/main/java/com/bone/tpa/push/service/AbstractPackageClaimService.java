package com.bone.tpa.push.service;

import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.dto.ClaimDetailPushDTO;

public abstract class AbstractPackageClaimService {
    public abstract String getHandlerCode();
    public void packageData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        checkData(claimNo, pushClaimContext);
        packageClaimData(claimNo, pushClaimContext, claimPushInfo);
        packageClaimImageData(claimNo, pushClaimContext, claimPushInfo);
        packageClaimDetailData(claimNo, pushClaimContext, claimPushInfo);
        packageClaimConclusionData(claimNo, pushClaimContext, claimPushInfo);
    }

    public abstract void checkData(Long claimNo, PushClaimContext pushClaimContext);
    public abstract void packageClaimData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void packageClaimImageData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void packageClaimDetailData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void packageClaimConclusionData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
}
