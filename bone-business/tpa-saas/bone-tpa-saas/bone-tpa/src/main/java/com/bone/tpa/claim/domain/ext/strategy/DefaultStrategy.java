package com.bone.tpa.claim.domain.ext.strategy;

import com.bone.tpa.claim.domain.ext.ExtensionStrategy;
import com.bone.tpa.sdk.claim.model.Claim;

public class DefaultStrategy implements ExtensionStrategy {

    @Override
    public String getInsuranceName() {
        return "";
    }

    @Override
    public void claimSubmitValidate(Claim claim) {

    }
}
