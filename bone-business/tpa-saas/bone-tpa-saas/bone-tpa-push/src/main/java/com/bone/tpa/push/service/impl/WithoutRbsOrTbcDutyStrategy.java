package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.tpa.push.bean.InvoicePropertyBean;
import com.bone.tpa.push.dto.ClaimConclusionDTO;
import com.bone.tpa.push.dto.ClaimDetailExtendDTO;
import com.bone.tpa.push.service.VisitDutyStrategy;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class WithoutRbsOrTbcDutyStrategy implements VisitDutyStrategy {

    @Override
    public String calculateDuty(LiabilityConfig liabilityConfig,
                                Coverage coverage,
                                ClaimInvoice claimInvoice,
                                List<LiabilityMapping> dutyConfigs) {
        VisitTypeEnum visitTypeEnum = VisitTypeEnum.getByValue(claimInvoice.getVisitTypeCn());
        if (Objects.isNull(visitTypeEnum)) {
            throw new TpaBizException("无效的就诊类型");
        }
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream().filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid()) &&
                Objects.equals(r.getInvoiceMedicalType(), visitTypeEnum.getCode())).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(filterConfigs)) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            return dutyConfig.getInsuranceCompanyLiability() + dutyConfig.getInsuranceCompanyLiabilitySub();
        }

        return null;
    }

    @Override
    public void setClaimDetailExtendFieldValue(ClaimDetailExtendDTO claimDetailExtendDTO, LiabilityConfig liabilityConfig, Coverage coverage, ClaimInvoice claimInvoice, List<LiabilityMapping> dutyConfigs) {
        VisitTypeEnum visitTypeEnum = VisitTypeEnum.getByValue(claimInvoice.getVisitTypeCn());
        if (Objects.isNull(visitTypeEnum)) {
            throw new TpaBizException("无效的就诊类型");
        }
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream().filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid()) &&
                Objects.equals(r.getInvoiceMedicalType(), visitTypeEnum.getCode())).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(filterConfigs)) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            //险种代码
            claimDetailExtendDTO.setInsuType(dutyConfig.getInsuranceCompanyCoverage());
            //责任代码
            claimDetailExtendDTO.setDutyCode(dutyConfig.getInsuranceCompanyLiability());
            //责任子码
            claimDetailExtendDTO.setDutySubcode(dutyConfig.getInsuranceCompanyLiabilitySub());
        }
    }

    @Override
    public Set<String> getDutyIds(List<ClaimInvoice> claimInvoices) {
        HashSet<String> dutyList = new HashSet<>();
        for (ClaimInvoice invoice : claimInvoices) {
            List<String> strings = Arrays.asList(invoice.getRelateLiability().split(","));
            for (String string : strings) {
                dutyList.add(string + "_" + invoice.getVisitType());
            }
        }
        return dutyList;
    }

    @Override
    public void setConclusionFieldValue(ClaimConclusionDTO claimConclusionDTO, LiabilityConfig liabilityConfig, Coverage coverage, ClaimInvoice claimInvoice, List<LiabilityMapping> dutyConfigs) {
        VisitTypeEnum visitTypeEnum = VisitTypeEnum.getByValue(claimInvoice.getVisitTypeCn());
        if (Objects.isNull(visitTypeEnum)) {
            throw new TpaBizException("无效的就诊类型");
        }
        List<LiabilityMapping> filterConfigs = dutyConfigs.stream().filter(r -> StringUtils.equals(r.getLiabilityUuid(), liabilityConfig.getUuid()) &&
                Objects.equals(r.getInvoiceMedicalType(), visitTypeEnum.getCode())).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(filterConfigs)) {
            LiabilityMapping dutyConfig = filterConfigs.get(0);
            claimConclusionDTO.setDuty(dutyConfig.getInsuranceCompanyLiability());
            claimConclusionDTO.setDutySubcode(dutyConfig.getInsuranceCompanyLiabilitySub());
            claimConclusionDTO.setAccidentNature(dutyConfig.getClaimAccident());
            claimConclusionDTO.setInsuType(dutyConfig.getInsuranceCompanyCoverage());
        }
    }

    @Override
    public InvoicePropertyBean getInvoicePropertyBean(String dutyId, List<ClaimInvoice> invoicesList) {
        InvoicePropertyBean invoicePropertyBean = new InvoicePropertyBean();
        String[] dutyAndInvoiceType = dutyId.split("_");
        invoicePropertyBean.setDutyId(dutyAndInvoiceType[0]);
        invoicePropertyBean.setTreatmentType(dutyAndInvoiceType[1]);
        //过滤包含此责任及发票类型的发票
        invoicePropertyBean.setClaimInvoices(invoicesList.stream().filter(r -> StringUtils.equals(r.getVisitType(),dutyAndInvoiceType[1]) &&
                StringUtils.contains(r.getRelateLiability(), dutyAndInvoiceType[0])).collect(Collectors.toList()));
        return invoicePropertyBean;
    }
}
