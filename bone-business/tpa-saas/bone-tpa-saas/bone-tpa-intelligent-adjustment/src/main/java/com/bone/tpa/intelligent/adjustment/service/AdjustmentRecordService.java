package com.bone.tpa.intelligent.adjustment.service;

import com.bone.core.util.BizContextUtils;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.intelligent.adjustment.enums.QuotaControllerTypeEnum;
import com.bone.tpa.intelligent.adjustment.enums.TimesLimitTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.AdjustmentDetailFailReasonEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharing;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceAdjustmentContext;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.model.liability.TimesLimit;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.dao.impl.AdjustmentRecordRepository;
import com.bone.tpa.sdk.dao.impl.AdjustmentResultRepository;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.enums.AccountTypeEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.claim.enums.ClaimStatusEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 理算
 * 数据准备服务
 *
 * 负责查询理算的基础数据
 */
@Service
@Slf4j
public class AdjustmentRecordService {

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    private AdjustmentRecordRepository adjustmentRecordRepository;

    @Autowired
    private AdjustmentResultRepository adjustmentResultRepository;


    /**
     * 获取该责任的理算记录
     */
    public List<AdjustmentRecord> getAdjustmentRecordByPlan(String uuid, Date startDate, Boolean includeOngoing, ClaimStakeholder outInsure) {
        Criteria<AdjustmentRecord> criteria = Criteria.create();
        criteria.eq(AdjustmentRecord::getPlanUuid, uuid);

        if (startDate != null) {
            criteria.eq(AdjustmentRecord::getCreateTime, startDate);
        }

        if (!includeOngoing) {
            criteria.eq(AdjustmentRecord::getRecordStatus, QuotaStatusEnum.CONFIRMED.getCode());
        } else {
            List<String> statusList = List.of(QuotaStatusEnum.CONFIRMED.getCode(), QuotaStatusEnum.FROZEN.getCode());

            criteria.in(AdjustmentRecord::getRecordStatus, statusList);
        }

        //还要限定取出的用户
        if (outInsure != null) {
            criteria.eq(AdjustmentRecord::getInsuredName, outInsure.getName());
            criteria.eq(AdjustmentRecord::getInsuredCertificateType, outInsure.getIdentityType());
            criteria.eq(AdjustmentRecord::getInsuredCertificateNumber, outInsure.getIdentityNo());
        }

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(criteria);

        return adjustmentRecordList;
    }

    /**
     * 获取该责任的理算记录
     */
    public List<AdjustmentRecord> getAdjustmentRecordByLiability(String uuid, Date startDate, Boolean includeOngoing) {
        Criteria<AdjustmentRecord> criteria = Criteria.create();
        criteria.eq(AdjustmentRecord::getLiabilityUuid, uuid);

        if (startDate != null) {
            criteria.eq(AdjustmentRecord::getCreateTime, startDate);
        }

        if (!includeOngoing) {
            criteria.eq(AdjustmentRecord::getRecordStatus, QuotaStatusEnum.CONFIRMED.getCode());
        } else {
            List<String> statusList = List.of(QuotaStatusEnum.CONFIRMED.getCode(), QuotaStatusEnum.FROZEN.getCode());

            criteria.in(AdjustmentRecord::getRecordStatus, statusList);
        }

        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordRepository.findByCriteria(criteria);

        return adjustmentRecordList;
    }


    /**
     * 插入全部的理算记录
     */
    public void insertRecordList(List<AdjustmentRecord> adjustmentRecordList) {
        adjustmentRecordRepository.insertBatch(adjustmentRecordList);
    }


    /**
     * 插入所有发票理算结果，并且组合为赔案理算结果
     *
     */
    public AdjustmentResult insertAndBuildResult(Map<String, List<AdjustmentRecord>> ongoingAdjustmentRecordMap,
                                                 Map<String, LiabilityConfig> liabilityMap, Claim claim) {
        AdjustmentResult adjustmentResult = new AdjustmentResult();
        adjustmentResult.setClaimId(claim.getId());
        adjustmentResult.setResultStatus(QuotaStatusEnum.FROZEN.getCode());
        //根据责任是公账还是个账，决定每条记录的金额记到哪个字段
        for (String uuid : ongoingAdjustmentRecordMap.keySet()) {
            LiabilityConfig liability = liabilityMap.get(uuid);
            List<AdjustmentRecord> adjustmentRecordList = ongoingAdjustmentRecordMap.get(uuid);
            BigDecimal liabilityTotalAmount = BigDecimal.ZERO;
            for (AdjustmentRecord record : adjustmentRecordList) {
                liabilityTotalAmount = liabilityTotalAmount.add(record.getPayoutAmount());
            }

            adjustmentResult.setPayoutAmount(adjustmentResult.getPayoutAmount().add(liabilityTotalAmount));

            if (liability.getAccountType().equals(AccountTypeEnum.PUBLIC.getCode())) {
                adjustmentResult.setPublicAmount(adjustmentResult.getPublicAmount().add(liabilityTotalAmount));
            } else if (liability.getAccountType().equals(AccountTypeEnum.PERSONAL.getCode())) {
                adjustmentResult.setIndividualAmount(adjustmentResult.getIndividualAmount().add(liabilityTotalAmount));
            }
        }
        adjustmentResult.setBizIdentityCode(claim.getBizIdentityCode());
        adjustmentResult.setTenantId(claim.getTenantId());

        //最后是构造结果和结果明细
        //result()
        //resultDetail()

        //将这条记录插入到库中
        adjustmentResultRepository.insert(adjustmentResult);

        return adjustmentResult;
    }


    /**
     * 清除理算结果
     *
     * @param recordIdList
     * @param resultIdList
     */
    public void clearAdjustmentResult(List<Long> recordIdList, List<Long> resultIdList) {
        adjustmentRecordRepository.deleteByIds(recordIdList);
        adjustmentResultRepository.deleteByIds(resultIdList);
    }


    /**
     * 更新理算结论
     * @param claimId
     */
    public void saveConclusion(AdjustConclusion adjustConclusion, Long claimId) {
        List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claimId, true);
        if (adjustmentResultList == null || adjustmentResultList.isEmpty() || adjustConclusion == null) {
            //理论上来说这里是有问题的，但是万一呢
            return;
        }

        AdjustmentResult adjustmentResult = adjustmentResultList.get(0);

        adjustmentResult.setResult(adjustConclusion.getPayOutConclusion());
        adjustmentResult.setResultDetail(adjustConclusion.getConclusionDetail());
        adjustmentResultRepository.update(adjustmentResult);
    }


    /**
     * 创建和入库失败的理算信息
     *
     * @param message
     */
    public AdjustmentRecord failRecordBuilder(InvoiceAdjustmentContext context, String message) {
        // 记录错误日志并返回错误结果
        LiabilityConfig liability = context.getLiability();
        AdjustmentRecord adjustmentRecord = AdjustmentRecord.builder()
                .outInsureTime(context.getClaim().getOutInsureTime())
                .invoiceId(context.getInvoice().getId())
                .invoiceNo(context.getInvoice().getInvoiceNo())
                .invoiceDate(context.getInvoice().getInvoiceDate())
                .visitDate(context.getInvoice().getVisitDate())
                .hospitalName(context.getInvoice().getHospitalName())
                .hospitalDepartment(context.getInvoice().getHospitalDepartmentName())
                .diagnosis(context.getInvoice().getDiagnosisCn())
                .accountType(liability.getAccountType())
                .insuredName(context.getOutInsure().getName())
                .insuredCertificateType(context.getOutInsure().getIdentityType())
                .insuredCertificateNumber(context.getOutInsure().getIdentityNo())
                .invoiceAmount(new BigDecimal("0.00"))
                .relatedId(context.getClaim().getId())
                .policyNo(context.getPolicy().getPolicyNo())
                .planUuid(context.getPlan().getUuid())
                .coverageName(context.getCoverage().getCoverageName())
                .liabilityUuid(liability.getUuid())
                .liabilityName(context.getRecordName())
                .version(liability.getVersion())
                .deductType("无")
                .deductAmount(new BigDecimal("0.00"))
                .deductDays(0)
                .quotaType((liability.getLiabilityLimit().getType() == null || liability.getLiabilityLimit().getType().isBlank()) ? "无" : liability.getLiabilityLimit().getType())
                .quotaDetail("无")
                .formula((liability.getFormula() == null || liability.getFormula().isBlank()) ? "无" : liability.getFormula())
                .visitType(context.getInvoice().getVisitType())
                .recordStatus(QuotaStatusEnum.FAILED.getCode())
                .operatorName(BizContextUtils.getUser())
                .payoutAmount(new BigDecimal("0.00")) //记录的总共赔付金额
                .resultDetail(message)
                .adjustmentResult("理算失败")
                .build();

        adjustmentRecord.setBizIdentityCode(context.getClaim().getBizIdentityCode());
        adjustmentRecord.setTenantId(context.getClaim().getTenantId());

        return adjustmentRecord;
    }


    /**
     * 控额信息格式化
     */
    public String quotaMapTransfer(InvoiceAdjustmentContext context) {
        Map<String, BigDecimal> quotaMap = context.getQuotaMap();
        List<String> parts = new ArrayList<>();
        /**
         * 计划
         * 险种
         * 共保
         * 直付
         * 责任
         * 就诊类型
         * 个人
         * 次期
         * 费用类型
         */

        // 计划
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.PLAN.getCode(),
                context.getPlan() != null ? context.getPlan().getPlanName() : null);

        // 险种
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.COVERAGE.getCode(),
                context.getCoverage() != null ? context.getCoverage().getCoverageName() : null);

        // 共保
        if (context.getSharingList() != null) {
            for (int i = 0; i < context.getSharingList().size(); i++) {
                String key = QuotaControllerTypeEnum.SHARING.getCode() + i;
                LiabilitySharing sharing = context.getSharingList().get(i);
                if (sharing != null) {
                    addIfPresent(parts, quotaMap, key, sharing.getShareCode());
                }
            }
        }

        // 直付个账
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode(),
                QuotaControllerTypeEnum.DIRECT_PERSONAL.getValue());

        // 直付公账
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.DIRECT_PUBLIC.getCode(),
                QuotaControllerTypeEnum.DIRECT_PUBLIC.getValue());

        // 责任
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.LIABILITY.getCode(),
                QuotaControllerTypeEnum.LIABILITY.getValue());

        // 就诊类型
        for (VisitTypeEnum visitType : VisitTypeEnum.values()) {
            addIfPresent(parts, quotaMap, visitType.getCode(), visitType.getValue());
        }

        // 个人专属额度
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.PERSONAL.getCode(),
                QuotaControllerTypeEnum.PERSONAL.getValue());

        // 次期限额
        addIfPresent(parts, quotaMap, QuotaControllerTypeEnum.TIMES.getCode(),
                QuotaControllerTypeEnum.TIMES.getValue());

        // 费用类型
        if (context.getFeeTypeList() != null) {
            for (int i = 0; i < context.getFeeTypeList().size(); i++) {
                String key = QuotaControllerTypeEnum.FEE_TYPE.getCode() + i;
                String feeType = context.getFeeTypeList().get(i);
                if (feeType != null) {
                    addIfPresent(parts, quotaMap, key, feeType);
                }
            }
        }

        // 留给完全没有控额的情况
        if (parts.isEmpty()) {
            return "无";
        }

        return String.join("\n", parts);
    }

    private void addIfPresent(List<String> parts, Map<String, BigDecimal> quotaMap,
                              String key, String name) {
        BigDecimal value = quotaMap.get(key);
        if (value != null && name != null && !name.isEmpty()) {
            parts.add(String.format("%s: %.2f", name, value));
        }
    }


    /**
     * 告警型理算信息处理
     */
    public String alarmQuotaProcessor(InvoiceAdjustmentContext context) {
        if (context.getAlarmQuotaMap().isEmpty()) {
            return null;
        }

        Map<String, BigDecimal> alarmMap = context.getAlarmQuotaMap();
        StringBuilder sb = new StringBuilder();

        //次日限额
        if (alarmMap.containsKey(QuotaControllerTypeEnum.TIMES.getCode())) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            BigDecimal amount = alarmMap.get(QuotaControllerTypeEnum.TIMES.getCode());

            if (context.getLiabilityPayAmount().compareTo(amount) > 0) {
                TimesLimit timesLimit = context.getLiability().getTimesLimit();

                if (timesLimit.getType().equals(TimesLimitTypeEnum.TIMES.getCode())) {
                    sb.append(String.format("本次赔付额超过预警额度:%.2f", timesLimit.getAmount()));
                }
                if (timesLimit.getType().equals(TimesLimitTypeEnum.DAYS.getCode())) {
                    sb.append(String.format("该日赔付额超过预警额度:%.2f", timesLimit.getAmount()));
                }
                if (timesLimit.getType().equals(TimesLimitTypeEnum.MONTHS.getCode())) {
                    sb.append(String.format("该月赔付额超过预警额度:%.2f", timesLimit.getAmount()));
                }
            }
        }


//        for (String type : alarmMap.keySet()) {
//            if (context.getLiabilityPayAmount().compareTo(alarmMap.get(type)) > 0) {
//                if (!sb.isEmpty()) {
//                    sb.append("\n");
//                }
//                sb.append(String.format("赔付额超过%s预警额度: %.2f", type, alarmMap.get(type)));
//            }
//        }

        return sb.toString();
    }

    /**
     * 根据理算记录直接调整发票金额
     */
    public ClaimInvoice processPayOutMoney(ClaimInvoice invoice, List<AdjustmentRecord> adjustmentRecordList) {
        //承担金额的总赔付额
        //先算出来，然后在实际理算时控额
        List<String> invoiceFeeHistoryPayoutList = adjustmentRecordList.stream().map(AdjustmentRecord::getFeePayoutList).toList();
        Map<String, BigDecimal> invoiceFeeHistoryPayoutMap = AdjustUtil.mergeJsonMaps(invoiceFeeHistoryPayoutList);

        //这个是三方已赔付
        //todo 朱子元 这里先偷个懒，目前反正不添加其他的基金金额
        BigDecimal paidAmount = invoice.getThirdPartyPaidAmount();

        if (invoiceFeeHistoryPayoutMap.containsKey("总自费")) {
            BigDecimal invoiceAmount = invoice.getTotalSelfPayAmount();
            invoice.setTotalSelfPayAmount(invoiceAmount.subtract(invoiceFeeHistoryPayoutMap.get("总自费")));

            paidAmount = paidAmount.add(invoiceFeeHistoryPayoutMap.get("总自费"));
        }

        if (invoiceFeeHistoryPayoutMap.containsKey("丙类自费")) {
            BigDecimal invoiceAmount = invoice.getClassCSelfPayAmount();
            invoice.setClassCSelfPayAmount(invoiceAmount.subtract(invoiceFeeHistoryPayoutMap.get("丙类自费")));

            paidAmount = paidAmount.add(invoiceFeeHistoryPayoutMap.get("丙类自费"));
        }

        if (invoiceFeeHistoryPayoutMap.containsKey("超限价自付")) {
            BigDecimal invoiceAmount = invoice.getExcessLimitSelfPayAmount();
            invoice.setExcessLimitSelfPayAmount(invoiceAmount.subtract(invoiceFeeHistoryPayoutMap.get("超限价自付")));

            paidAmount = paidAmount.add(invoiceFeeHistoryPayoutMap.get("超限价自付"));
        }

        if (invoiceFeeHistoryPayoutMap.containsKey("自付二")) {
            BigDecimal invoiceAmount = invoice.getSelfPayPart2Amount();
            invoice.setSelfPayPart2Amount(invoiceAmount.subtract(invoiceFeeHistoryPayoutMap.get("自付二")));

            paidAmount = paidAmount.add(invoiceFeeHistoryPayoutMap.get("自付二"));
        }

        if (invoiceFeeHistoryPayoutMap.containsKey("合理金额")) {
            BigDecimal invoiceAmount = invoice.getValidAmount();
            invoice.setValidAmount(invoiceAmount.subtract(invoiceFeeHistoryPayoutMap.get("合理金额")));

            paidAmount = paidAmount.add(invoiceFeeHistoryPayoutMap.get("合理金额"));
        }

        invoice.setThirdPartyPaidAmount(paidAmount);

        return invoice;
    }

}
