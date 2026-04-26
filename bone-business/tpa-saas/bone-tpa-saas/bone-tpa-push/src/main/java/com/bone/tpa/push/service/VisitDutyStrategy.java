package com.bone.tpa.push.service;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.tpa.push.bean.InvoicePropertyBean;
import com.bone.tpa.push.dto.ClaimConclusionDTO;
import com.bone.tpa.push.dto.ClaimDetailExtendDTO;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public interface VisitDutyStrategy {
    default String calculateDuty(LiabilityConfig liabilityConfig,
                                 Coverage coverage,
                                 ClaimInvoice claimInvoice,
                                 List<LiabilityMapping> dutyConfigs) {
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream()
                .filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid()))
                .collect(Collectors.toList());

        if (CollectionUtil.isNotEmpty(filterConfigs)) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            return dutyConfig.getInsuranceCompanyLiability() + dutyConfig.getInsuranceCompanyLiabilitySub();
        }
        return null;
    }

    default void setClaimDetailExtendFieldValue(ClaimDetailExtendDTO claimDetailExtendDTO,
                                        LiabilityConfig liabilityConfig,
                                        Coverage coverage,
                                        ClaimInvoice claimInvoice,
                                        List<LiabilityMapping> dutyConfigs) {
        //险种代码
        claimDetailExtendDTO.setInsuType(coverage.getCoverageCode());
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream().filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid())).collect(Collectors.toList());
        if (filterConfigs != null && filterConfigs.size() > 0) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            //责任代码
            claimDetailExtendDTO.setDutyCode(dutyConfig.getInsuranceCompanyLiability());
            //责任子码
            claimDetailExtendDTO.setDutySubcode(dutyConfig.getInsuranceCompanyLiabilitySub());
        }
    }

    default Set<String> getDutyIds(List<ClaimInvoice> claimInvoices) {
        HashSet<String> dutyList = new HashSet<>();
        for (ClaimInvoice invoice : claimInvoices) {
            dutyList.addAll(Arrays.asList(invoice.getRelateLiability().split(",")));
        }
        return dutyList;
    }

    default void setConclusionFieldValue(ClaimConclusionDTO claimConclusionDTO,
                                         LiabilityConfig liabilityConfig,
                                         Coverage coverage,
                                         ClaimInvoice claimInvoice,
                                         List<LiabilityMapping> dutyConfigs) {
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream().filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid())).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(filterConfigs)) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            claimConclusionDTO.setDuty(dutyConfig.getInsuranceCompanyLiability());
            claimConclusionDTO.setDutySubcode(dutyConfig.getInsuranceCompanyLiabilitySub());
            claimConclusionDTO.setAccidentNature(dutyConfig.getClaimAccident());
            claimConclusionDTO.setInsuType(dutyConfig.getInsuranceCompanyCoverage());
        }
    }

    default InvoicePropertyBean getInvoicePropertyBean(String dutyId,
                                                       List<ClaimInvoice> invoicesList) {
        InvoicePropertyBean invoicePropertyBean = new InvoicePropertyBean();
        invoicePropertyBean.setTreatmentType("");
        invoicePropertyBean.setClaimInvoices(invoicesList.stream().filter(r -> StringUtils.contains(r.getRelateLiability(),dutyId)).collect(Collectors.toList()));
        invoicePropertyBean.setDutyId(dutyId);
        return invoicePropertyBean;
    }
}
