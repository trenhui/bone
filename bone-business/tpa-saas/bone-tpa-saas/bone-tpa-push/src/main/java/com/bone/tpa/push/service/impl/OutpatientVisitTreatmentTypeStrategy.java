package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.dto.ClaimDetailDTO;
import com.bone.tpa.push.enums.TreatmentTypeEnum;
import com.bone.tpa.push.service.VisitTreatmentTypeStrategy;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OutpatientVisitTreatmentTypeStrategy implements VisitTreatmentTypeStrategy {


    @Override
    public void setClaimDetailFieldValue(ClaimInvoice claimInvoice, ClaimDetailDTO claimDetailDTO) {
        claimDetailDTO.setHospitalDays(BigDecimal.ZERO);
        //赔付天数
        claimDetailDTO.setCompensateDays(0);
        if (TreatmentTypeEnum.mz.getValue().toString().equals(claimInvoice.getVisitType())) {
            claimDetailDTO.setVisitType(2);
        } else if (TreatmentTypeEnum.yf.getValue().toString().equals(claimInvoice.getVisitType()) ||
                TreatmentTypeEnum.other.getValue().toString().equals(claimInvoice.getVisitType()) ||
                TreatmentTypeEnum.mz_tmb.getValue().toString().equals(claimInvoice.getVisitType())) {
            claimDetailDTO.setVisitType(Integer.valueOf(claimInvoice.getVisitType()));
        }
    }
}
