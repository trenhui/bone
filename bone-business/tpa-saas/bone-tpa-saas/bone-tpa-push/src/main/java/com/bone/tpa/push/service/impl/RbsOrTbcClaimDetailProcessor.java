package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.service.ClaimDetailProcessor;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimDetailExtend;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bone.tpa.push.constants.CommonConstant.SPECIAL_BRANCH_NAMES;
import static com.bone.tpa.push.constants.CommonConstant.SPECIAL_INSURANCE_NAMES;

@Component
public class RbsOrTbcClaimDetailProcessor implements ClaimDetailProcessor {

    @Override
    public boolean supports(String insuranceName, String branchName) {
        return SPECIAL_INSURANCE_NAMES.contains(insuranceName)
                || SPECIAL_BRANCH_NAMES.contains(branchName)
                || (insuranceName != null && insuranceName.contains("鼎和"));
    }

    @Override
    public List<TbOverClaimDetailExtend> process(
            List<TbOverClaimDetailExtend> detailExtends,
            String finalDutyId,
            String finalTreatmentType) {
        return getTbOverClaimDetailExtends(detailExtends, finalDutyId, finalTreatmentType);
    }

    private List<TbOverClaimDetailExtend> getTbOverClaimDetailExtends(
            List<TbOverClaimDetailExtend> detailExtends, String finalDutyId, String finalTreatmentType) {
        List<TbOverClaimDetailExtend> tbOverClaimDetailExtends = detailExtends.stream().filter(rr -> StringUtils.isEmpty(finalTreatmentType)
                ? rr.getTpaDutyId().equals(finalDutyId) : rr.getTpaDutyId().equals(finalDutyId) && rr.getInvoiceType().toString().equals(finalTreatmentType)).collect(Collectors.toList());
        return tbOverClaimDetailExtends;
    }
}
