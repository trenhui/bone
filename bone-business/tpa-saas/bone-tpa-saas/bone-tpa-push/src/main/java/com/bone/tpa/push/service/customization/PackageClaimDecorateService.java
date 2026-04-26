package com.bone.tpa.push.service.customization;

import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.dto.ClaimDetailPushDTO;
import com.bone.tpa.push.service.AbstractPackageClaimService;

/**
 * @Author feihaiming
 * @create 2025/10/20 11:31
 */
public abstract class PackageClaimDecorateService extends AbstractPackageClaimService {

    private AbstractPackageClaimService packageClaimService;

    public PackageClaimDecorateService(AbstractPackageClaimService packageClaimService) {
        this.packageClaimService = packageClaimService;
    }

    @Override
    public void packageClaimData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        packageClaimService.packageClaimData(claimNo, pushClaimContext, claimPushInfo);
        customizedPackageClaimData(pushClaimContext, claimPushInfo);
    }

    @Override
    public void packageClaimImageData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        packageClaimService.packageClaimImageData(claimNo, pushClaimContext, claimPushInfo);
        customizedPackageClaimImageData(pushClaimContext, claimPushInfo);
    }

    @Override
    public void packageClaimDetailData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        packageClaimService.packageClaimDetailData(claimNo, pushClaimContext, claimPushInfo);
        customizedPackageClaimDetailData(pushClaimContext, claimPushInfo);
    }

    @Override
    public void packageClaimConclusionData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        packageClaimService.packageClaimConclusionData(claimNo, pushClaimContext, claimPushInfo);
        customizedPackageClaimConclusionData(pushClaimContext, claimPushInfo);
    }

    public abstract void customizedPackageClaimData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void customizedPackageClaimImageData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void customizedPackageClaimDetailData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
    public abstract void customizedPackageClaimConclusionData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo);
}
