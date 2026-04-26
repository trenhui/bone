package com.bone.tpa.push.service;

import com.bone.tpa.push.dto.ClaimDetailDTO;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;

public interface VisitTreatmentTypeStrategy {
    void setClaimDetailFieldValue(
            ClaimInvoice claimInvoice,
            ClaimDetailDTO claimDetailDTO
    );
}
