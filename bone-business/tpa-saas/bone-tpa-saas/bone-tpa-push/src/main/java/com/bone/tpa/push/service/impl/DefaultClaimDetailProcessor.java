package com.bone.tpa.push.service.impl;

import com.bone.tpa.push.service.ClaimDetailProcessor;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimDetailExtend;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DefaultClaimDetailProcessor implements ClaimDetailProcessor {

    @Override
    public boolean supports(String insuranceName, String branchName) {
        // 默认策略，对所有未匹配的情况生效
        return true;
    }

    @Override
    public List<TbOverClaimDetailExtend> process(
            List<TbOverClaimDetailExtend> detailExtends,
            String finalDutyId,
            String finalTreatmentType) {
        return getOverClaimDetailExtends(detailExtends, finalDutyId, finalTreatmentType);
    }

    private List<TbOverClaimDetailExtend> getOverClaimDetailExtends(
            List<TbOverClaimDetailExtend> detailExtends, String finalDutyId, String finalTreatmentType) {
        List<TbOverClaimDetailExtend> tbOverClaimDetailExtends = detailExtends.stream().filter(rr -> StringUtils.isEmpty(finalTreatmentType)
                ? rr.getTpaDutyId().equals(finalDutyId) : rr.getTpaDutyId().equals(finalDutyId) && rr.getTreatmenttype().equals(finalTreatmentType)).collect(Collectors.toList());
        return tbOverClaimDetailExtends;
    }
}
