package com.bone.tpa.claim.flow.jumpcondition;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.audit.application.ConfigSupportService;
import com.bone.tpa.audit.infrastructure.feign.request.QueryReviewInfoRequest;
import com.bone.tpa.audit.infrastructure.feign.request.ReviewRuleRequest;
import com.bone.tpa.audit.infrastructure.feign.response.QueryReviewInfoResponse;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.push.enums.TreatmentTypeEnum;
import com.bone.tpa.push.feign.response.CompanyInfoResponse;
import com.bone.tpa.push.service.ConfigAdapterAbstractHandler;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * tpa 提供进入复核的准入规则
 */
@Service
@Slf4j
public class TpaReviewApproveRule implements JumpConditionIntterface {
    @Autowired
    private CommonLogService commonLogService;

    @Autowired
    private ConfigSupportService configSupportService;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

    @Autowired
    @Qualifier("pushConfigAdapterHandler")
    private ConfigAdapterAbstractHandler configAdapterHandler;

    /**
     * beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "tpaReviewApproveRule";
    }

    /**
     * 是否命中
     *
     * @param claim
     * @param conditionVO
     * @return
     */
    @Override
    public boolean isMatch(Claim claim, FlowConditionVO conditionVO) {
        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAdjustmentRecordByClaim(claim.getId(), true);
        List<ClaimInvoice> invoiceList = claimInfoService.getInvoiceListWithoutException(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId());

        CompanyInfoResponse companyInfoResponse = configAdapterHandler.handleCompanyInfo(claim.getInsuranceName());
        if (Objects.isNull(companyInfoResponse)) {
            log.warn("未找到机构信息" + claim.getInsuranceName());
            return false;
        }

        QueryReviewInfoRequest queryReviewInfoRequest = new QueryReviewInfoRequest();
        ReviewRuleRequest request = new ReviewRuleRequest();
        queryReviewInfoRequest.setReviewRuleDTO(request);
        queryReviewInfoRequest.setClaimNo(claim.getId());
        queryReviewInfoRequest.setPolicyNo(claim.getPolicyNo());
        queryReviewInfoRequest.setInsuraceId(companyInfoResponse.getCompanyId());
        queryReviewInfoRequest.setUserName(BizContextUtils.getUser());

        if (CollectionUtil.isNotEmpty(invoiceList)) {
            invoiceList.sort((o1, o2) -> o2.getValidAmount().compareTo(o2.getValidAmount()));
            request.setReasonAmountTotal(new BigDecimal(invoiceList.stream().mapToDouble(t -> t.getValidAmount().doubleValue()).sum()));
            request.setReasonAmountSingle(invoiceList.get(0).getValidAmount());
            request.setOutpatientInvoiceCount(invoiceList.stream().filter(t -> Objects.equals(t.getVisitType(), TreatmentTypeEnum.mz_tmb.getValue()) ||
                    Objects.equals(t.getVisitType(), TreatmentTypeEnum.mz.getValue())).collect(Collectors.toList()).size());
            request.setHospitalizedInvoiceCount(invoiceList.stream().filter(t -> Objects.equals(t.getVisitType(), TreatmentTypeEnum.zy.getValue())).collect(Collectors.toList()).size());
            request.setBuyMedicineInvoiceCount(invoiceList.stream().filter(t -> Objects.equals(t.getVisitType(), TreatmentTypeEnum.yf.getValue())).collect(Collectors.toList()).size());
        }

        if (CollectionUtil.isNotEmpty(adjustmentRecordList)) {
            adjustmentRecordList.sort((o1, o2) -> o2.getPayoutAmount().compareTo(o2.getPayoutAmount()));
            request.setPayAmountTotal(new BigDecimal(adjustmentRecordList.stream().mapToDouble(t -> t.getPayoutAmount().doubleValue()).sum()));
            request.setPayAmountSingle(adjustmentRecordList.get(0).getPayoutAmount());
            request.setZeroCompensationInvoiceCount(adjustmentRecordList.stream().filter(t -> Objects.nonNull(t.getCompensateType())).collect(Collectors.toList()).size());//todo 没有明确的拒赔字段
        }

        if (CollectionUtil.isNotEmpty(invoiceList) && CollectionUtil.isNotEmpty(adjustmentRecordList)) {
            AdjustmentRecord targetRecord = adjustmentRecordList.get(0);
            List<String> liabilityUuids = invoiceList.stream().filter(claimInvoice -> StringUtils.isNotEmpty(claimInvoice.getRelateLiability())).map(t -> t.getRelateLiability().split(",")).flatMap(Arrays::stream)
                    .distinct().collect(Collectors.toList());
            List<LiabilityConfig> liabilityConfigs = liabilityInfoBasicService.queryLiabilityByUuidAndVersion(liabilityUuids, targetRecord.getVersion());
            if (CollectionUtil.isNotEmpty(liabilityConfigs)) {
                request.setAllowanceCount(liabilityUuids.stream().filter(t -> liabilityConfigs.stream().filter(s -> LiabilityTypeEnum.ALLOWANCE.equals(s.getLiabilityType())).map(LiabilityConfig::getUuid).collect(Collectors.toList()).contains(t)).collect(Collectors.toList()).size());
                request.setReimbursementCount(liabilityUuids.stream().filter(t -> liabilityConfigs.stream().filter(s -> LiabilityTypeEnum.REIMBURSEMENT.equals(s.getLiabilityType())).map(LiabilityConfig::getUuid).collect(Collectors.toList()).contains(t)).collect(Collectors.toList()).size());
                request.setFixedAmountCount(liabilityUuids.stream().filter(t -> liabilityConfigs.stream().filter(s -> LiabilityTypeEnum.FIXED_AMOUNT.equals(s.getLiabilityType())).map(LiabilityConfig::getUuid).collect(Collectors.toList()).contains(t)).collect(Collectors.toList()).size());
            }
        }

        QueryReviewInfoResponse response = configSupportService.queryReviewInfo(queryReviewInfoRequest);
        if (Objects.nonNull(response) && response.getMustReview()) {
            commonLogService.addLogASync(claim.getId().toString(),
                    CommonLogType.FLOW_LOG,"TpaReviewApproveRule return true");
            return true;
        }else{
            commonLogService.addLogASync(claim.getId().toString(),
                    CommonLogType.FLOW_LOG,"TpaReviewApproveRule return false,msg:{}",response.getMsg());
            return false;
        }

    }
}
