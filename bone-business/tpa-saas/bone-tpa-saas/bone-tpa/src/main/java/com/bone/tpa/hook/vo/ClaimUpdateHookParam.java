package com.bone.tpa.hook.vo;

import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.sdk.claim.model.Claim;
import lombok.Data;

@Data
public class ClaimUpdateHookParam {
    private Claim claimExist;
    private ClaimDetailObject claimDetailObject;
}
