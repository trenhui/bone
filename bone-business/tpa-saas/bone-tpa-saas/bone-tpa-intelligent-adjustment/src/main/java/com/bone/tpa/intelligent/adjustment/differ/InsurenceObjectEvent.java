package com.bone.tpa.intelligent.adjustment.differ;

import com.bone.tpa.sdk.claim.model.Claim;

public interface InsurenceObjectEvent {

    boolean isMatch(Claim claim);



    /**
     * 当匹配医院失败的时候，获取默认医院
     * @param claim
     * @return
     */
    String getDefaultHosptialName(Long claimId);


}
