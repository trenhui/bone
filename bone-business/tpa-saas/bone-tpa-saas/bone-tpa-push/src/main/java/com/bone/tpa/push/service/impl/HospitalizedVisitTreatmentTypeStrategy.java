package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.dto.ClaimDetailDTO;
import com.bone.tpa.push.enums.TreatmentTypeEnum;
import com.bone.tpa.push.service.VisitTreatmentTypeStrategy;
import com.bone.tpa.push.util.DateTool;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;

@Component
public class HospitalizedVisitTreatmentTypeStrategy implements VisitTreatmentTypeStrategy {


    @Override
    public void setClaimDetailFieldValue(ClaimInvoice claimInvoice, ClaimDetailDTO claimDetailDTO) {
        if (claimInvoice.getHospitalDays() != null) {
            claimDetailDTO.setHospitalDays(new BigDecimal(claimInvoice.getHospitalDays()));
        } else {
            Date beginDate = DateTool.getFirstDate(claimInvoice.getHospitalPeriod());
            Date endDate = DateTool.getLastDate(claimInvoice.getHospitalPeriod());
            if (Objects.nonNull(beginDate) && Objects.nonNull(endDate)) {
                long daysBetween = (endDate.getTime() - beginDate.getTime() + 1000000) / (60 * 60 * 24 * 1000);
                claimDetailDTO.setHospitalDays(BigDecimal.valueOf(daysBetween));
            }
        }
        claimDetailDTO.setCompensateDays(claimDetailDTO.getHospitalDays().intValue());
        claimDetailDTO.setVisitType(1);
    }
}
