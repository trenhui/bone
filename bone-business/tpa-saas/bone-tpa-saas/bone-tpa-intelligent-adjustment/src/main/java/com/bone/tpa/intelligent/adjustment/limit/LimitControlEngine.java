package com.bone.tpa.intelligent.adjustment.limit;

import com.bone.core.exception.BizException;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.feign.DirectPaymentFeignClient;
import com.bone.tpa.facade.feign.PooledAccountFeignClient;
import com.bone.tpa.facade.request.QueryBalanceRequest;
import com.bone.tpa.facade.vo.PooledAccountBalance;
import com.bone.tpa.facade.vo.QueryBalanceResponse;
import com.bone.tpa.intelligent.adjustment.enums.*;
import com.bone.tpa.intelligent.adjustment.limit.service.PersonalQuotaService;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.sdk.adjustment.enums.AccountTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaControlSourceEnum;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
public class LimitControlEngine {

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    @Autowired
    private PersonalQuotaService personalQuotaService;

    @Autowired
    private PooledAccountFeignClient pooledAccountFeignClient;

    @Autowired
    private DirectPaymentFeignClient directPaymentFeignClient;

    /**
     * 额度控制核心方法
     */
    public BigDecimal limitControl(InvoiceAdjustmentContext context) {
        Map<String, BigDecimal> quotaDetailMap = new HashMap<>();
        Map<String, BigDecimal> alarmQuotaMap = new HashMap<>();

        List<AdjustmentRecord> adjustmentRecordList = context.getHistoryAdjustmentRecord();

        //该责任全部历史记录总共的赔付和
        BigDecimal totalHistoryAmount = BigDecimal.ZERO;
        if (adjustmentRecordList != null) {
            totalHistoryAmount = adjustmentRecordList.stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

//        BigDecimal remainLimit = BigDecimal.valueOf(Integer.MAX_VALUE);
        //取出控额方式
        LiabilityConfig liability = context.getLiability();
        LiabilityLimit liabilityLimit = liability.getLiabilityLimit();
        if (liabilityLimit.getType().equals(LimitControlTypeEnum.PERSONAL.getCode())
                || liabilityLimit.getType().equals(LimitControlTypeEnum.LIABILITY_PERSONAL.getCode())) {

            //然后去查询这个人的个人额度
            if (context.getPersonalQuota() == null) {
                log.warn("赔案 {} 开始理算, 但是出险人 {} 没有个人额度或个人额度错误。保单号 {}", context.getClaim().getClaimNo(), context.getOutInsure().getName(), context.getPolicy().getPolicyNo());
                quotaDetailMap.put(QuotaControllerTypeEnum.PERSONAL.getCode(), BigDecimal.ZERO);
            } else {
                //这里要筛选所有涉及到个人额度的理算记录
                BigDecimal totalPersonAmount = adjustmentRecordList.stream().filter(t -> (t.getQuotaType().equals(LimitControlTypeEnum.LIABILITY_PERSONAL.getCode())
                        || t.getQuotaType().equals(LimitControlTypeEnum.PERSONAL.getCode()))).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

                quotaDetailMap.put(QuotaControllerTypeEnum.PERSONAL.getCode(), calculateQuota(context.getPersonalQuota().getQuotaInitial(), totalPersonAmount));
            }
        }
        if (liabilityLimit.getType().equals(LimitControlTypeEnum.LIABILITY.getCode()) ||
                liabilityLimit.getType().equals(LimitControlTypeEnum.LIABILITY_VISIT.getCode()) ||
                liabilityLimit.getType().equals(LimitControlTypeEnum.LIABILITY_PERSONAL.getCode())) {
            //责任额度
            quotaDetailMap.put(QuotaControllerTypeEnum.LIABILITY.getCode(), calculateQuota(liabilityLimit.getLiabilityLimit(), totalHistoryAmount));
        }
        if(liabilityLimit.getType().equals(LimitControlTypeEnum.LIABILITY_VISIT.getCode())) {
            //就诊类别
            //查询出总金额
            BigDecimal visitTypeHistoryTotal = adjustmentRecordList.stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid()) &&
                            t.getVisitType().equals(context.getInvoice().getVisitType()))
                            .map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            if(context.getInvoice().getVisitTypeCn().equals(VisitTypeEnum.OUTPATIENT_EMERGENCY.getValue())) {
                quotaDetailMap.put(VisitTypeEnum.OUTPATIENT_EMERGENCY.getCode(), calculateQuota(liabilityLimit.getOutpatientEmergencyLimit(), visitTypeHistoryTotal));
            } else if(context.getInvoice().getVisitTypeCn().equals(VisitTypeEnum.INPATIENT.getValue())) {
                quotaDetailMap.put(VisitTypeEnum.INPATIENT.getCode(), calculateQuota(liabilityLimit.getInpatientLimit(), visitTypeHistoryTotal));
            } else if(context.getInvoice().getVisitTypeCn().equals(VisitTypeEnum.PHARMACY.getValue())) {
                quotaDetailMap.put(VisitTypeEnum.PHARMACY.getCode(), calculateQuota(liabilityLimit.getPharmacyLimit(), visitTypeHistoryTotal));
            } else if(context.getInvoice().getVisitTypeCn().equals(VisitTypeEnum.SPECIAL_CLINIC.getValue())) {
                quotaDetailMap.put(VisitTypeEnum.SPECIAL_CLINIC.getCode(), calculateQuota(liabilityLimit.getSpecialClinicLimit(), visitTypeHistoryTotal));
            }
        }

        //计划额度
        Plan plan = context.getPlan();
        if (plan.getPlanLimit().compareTo(BigDecimal.ZERO) > 0) {
            //该计划维度的全部历史记录的总赔付额
            BigDecimal totalHistoryAmountPlan = BigDecimal.ZERO;
            if (adjustmentRecordList != null) {
                totalHistoryAmountPlan = adjustmentRecordList.stream().filter(t -> t.getPlanUuid().equals(plan.getUuid())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            quotaDetailMap.put(QuotaControllerTypeEnum.PLAN.getCode(), calculateQuota(plan.getPlanLimit(), totalHistoryAmountPlan));
        }

        //险种额度
        Coverage coverage = context.getCoverage();
        if (coverage.getCoverageLimit().compareTo(BigDecimal.ZERO) > 0) {
            //该险种维度的全部历史记录的总赔付额
            List<LiabilityConfig> liabilityConfigList = context.getAllLiability().stream().filter(t -> t.getCoverageId().equals(coverage.getId())).collect(Collectors.toList());
            List<String> uuidList = liabilityConfigList.stream().map(LiabilityConfig::getUuid).collect(Collectors.toList());

            BigDecimal totalHistoryAmountCoverage = BigDecimal.ZERO;
            if (adjustmentRecordList != null) {
                totalHistoryAmountCoverage = adjustmentRecordList.stream().filter(t -> uuidList.contains(t.getLiabilityUuid())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            quotaDetailMap.put(QuotaControllerTypeEnum.COVERAGE.getCode(), calculateQuota(coverage.getCoverageLimit(), totalHistoryAmountCoverage));
        }

        //共保额度
        List<LiabilitySharing> sharingList = context.getSharingList();
        Map<String, List<LiabilitySharingRelation>> sharingRelationMap = context.getSharingRelationList().stream().collect(Collectors.groupingBy(LiabilitySharingRelation::getShareCode));
        for (LiabilitySharing sharing : sharingList) {
            //该共保维度的全部历史记录的总赔付额
            List<LiabilitySharingRelation> sharingRelationList = sharingRelationMap.get(sharing.getShareCode());
            List<String> uuidList = sharingRelationList.stream().map(LiabilitySharingRelation::getLiabilityUuid).collect(Collectors.toList());

            BigDecimal totalHistoryAmountSharing = BigDecimal.ZERO;
            if (adjustmentRecordList != null) {
                totalHistoryAmountSharing = adjustmentRecordList.stream().filter(t -> uuidList.contains(t.getLiabilityUuid())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            quotaDetailMap.put(QuotaControllerTypeEnum.SHARING.getCode() + sharingList.indexOf(sharing), calculateQuota(sharing.getShareLimit(), totalHistoryAmountSharing));
        }

        //次日限额。这里如果不是强制就不在这里看了，等到后面再说
        TimesLimit timesLimit = liability.getTimesLimit();
        if (timesLimit != null) {
            Set<AdjustmentRecord> sameRecord = null;

            if (timesLimit.getType().equals(TimesLimitTypeEnum.TIMES.getCode())) {
                //如果是按次
                if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.CLAIM.getCode())) {
                    //如果是一赔案记一次，去取出与当前在同一赔案的记录（基本上意味着是在途的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && t.getRelatedId().equals(context.getClaim().getId()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.DAY.getCode())) {
                    //如果是一天记一次，去取出与当前在同一天的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getVisitDate(), context.getInvoice().getVisitDate()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.HOSPITAL.getCode())) {
                    if (context.getInvoice().getHospitalName() == null || context.getInvoice().getHospitalName().isBlank()) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失医院信息");
                    }
                    //如果是一医院记一次，去取出与当前在同一天且同一医院的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getVisitDate(), context.getInvoice().getVisitDate())
                                    && t.getHospitalName().equals(context.getInvoice().getHospitalName()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.DEPARTMENT.getCode())) {
                    if (context.getInvoice().getHospitalName() == null || context.getInvoice().getHospitalName().isBlank()) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失医院信息");
                    }
                    if (context.getInvoice().getHospitalDepartmentName() == null || context.getInvoice().getHospitalDepartmentName().isBlank()) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失医院科别信息");
                    }
                    //如果是一科室记一次，去取出与当前在同一天同一医院且同一科室的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getVisitDate(), context.getInvoice().getVisitDate())
                                    && t.getHospitalName().equals(context.getInvoice().getHospitalName())
                                    && t.getHospitalDepartment().equals(context.getInvoice().getHospitalDepartmentName()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.DISEASE.getCode())) {
                    if (context.getInvoice().getHospitalName() == null || context.getInvoice().getHospitalName().isBlank()) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失医院信息");
                    }
                    if (context.getInvoice().getDiagnosisCn() == null || context.getInvoice().getDiagnosisCn().isBlank()) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票缺失诊断信息");
                    }
                    //如果是一疾病记一次，去取出与当前在同一天同一医院且同一疾病的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getVisitDate(), context.getInvoice().getVisitDate())
                                    && t.getHospitalName().equals(context.getInvoice().getHospitalName())
                                    && t.getDiagnosis().equals(context.getInvoice().getDiagnosisCn()))
                            .collect(Collectors.toSet());
                }
            } else if (timesLimit.getType().equals(TimesLimitTypeEnum.DAYS.getCode())) {
                //如果是按日
                if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.CLAIM_SAME_DAY.getCode())) {
                    if (context.getClaim().getOutInsureTime() == null) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "赔案缺失出险时间");
                    }
                    //如果是一赔案记一次，去取出与当前在同一赔案日的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getOutInsureTime(), context.getClaim().getOutInsureTime()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.INVOICE_SAME_DAY.getCode())) {
                    //如果是一天记一次，去取出与当前在同一天的记录。和上面是一样的
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameDay(t.getVisitDate(), context.getInvoice().getVisitDate()))
                            .collect(Collectors.toSet());
                }
            } else if (timesLimit.getType().equals(TimesLimitTypeEnum.MONTHS.getCode())) {
                //如果是按日
                if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.CLAIM_SAME_MONTH.getCode())) {
                    if (context.getClaim().getOutInsureTime() == null) {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "赔案缺失出险时间");
                    }
                    //如果是一赔案记一次，去取出与当前在同一月的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameMonth(t.getOutInsureTime(), context.getClaim().getOutInsureTime()))
                            .collect(Collectors.toSet());
                } else if (timesLimit.getDefinition().equals(TimesLimitDefinitionEnum.INVOICE_SAME_MONTH.getCode())) {
                    //如果是一天记一次，去取出与当前在同一月的记录
                    sameRecord = adjustmentRecordList.stream()
                            .filter(t -> t.getLiabilityUuid().equals(liability.getUuid())
                                    && DateUtil.isSameMonth(t.getVisitDate(), context.getInvoice().getVisitDate()))
                            .collect(Collectors.toSet());
                }
            }

            if (sameRecord == null) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "次日限定设置错误: " + liability.getLiabilityName() );
            }

            BigDecimal timesHistoryAmount = sameRecord.stream().map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add); // 累加求和

            if (timesLimit.getControlType().equals(ControlTypeEnum.FORCE.getCode())) {
                quotaDetailMap.put(QuotaControllerTypeEnum.TIMES.getCode(), calculateQuota(timesLimit.getAmount(), timesHistoryAmount));
            } else {
                alarmQuotaMap.put(QuotaControllerTypeEnum.TIMES.getCode(), calculateQuota(timesLimit.getAmount(), timesHistoryAmount));
            }

        }

        //三方限额
        QuotaController quotaController = liability.getQuotaController();
        if (quotaController.getType().equals(QuotaControlSourceEnum.TPA.getCode())) {
            //这里什么也没有
        } else if (quotaController.getType().equals(QuotaControlSourceEnum.DIRECT.getCode())) {
            String accountType = liability.getAccountType();

            //获取公账额度
            if (accountType.equals(AccountTypeEnum.PUBLIC.getCode())) {
                if (context.getPublicAccountMap().containsKey(quotaController.getPolicyNo())) {
                    quotaDetailMap.put(QuotaControllerTypeEnum.DIRECT_PUBLIC.getCode(),
                            calculateQuota(context.getPublicAccountMap().get(quotaController.getPolicyNo()),
                                    context.getPublicAccountPayMap().get(quotaController.getPolicyNo())));
                } else {
                    ApiResult<PooledAccountBalance> balance = pooledAccountFeignClient
                            .queryAccount(quotaController.getPolicyNo());
                    if (balance.getCode() != 0) {
                        throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR,  "查询公账失败：" + balance.getMessage());
                    }
                    log.info("从直付获得{}公账额度: {}", balance.getData().getSlipCode(), balance.getData().getBalance());

                    context.setDirectAmount(balance.getData().getBalance());
                    quotaDetailMap.put(QuotaControllerTypeEnum.DIRECT_PUBLIC.getCode(), balance.getData().getBalance());
                }
            }

            //获取个账额度
            if (accountType.equals(AccountTypeEnum.PERSONAL.getCode())) {
                if (context.getPersonalAccount() != null) {
                    quotaDetailMap.put(QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode(),
                            calculateQuota(context.getPersonalAccount(), context.getPersonalAccountPay()));
                } else {
                    //获取直付额度
                    QueryBalanceRequest getPeopleInfoRequest = new QueryBalanceRequest();
                    getPeopleInfoRequest.setPersonCertId(context.getOutInsure().getIdentityNo());
                    getPeopleInfoRequest.setPersonName(context.getOutInsure().getName());
                    getPeopleInfoRequest.setSlipCode(context.getPolicy().getPolicyNo());

                    ApiResult<List<QueryBalanceResponse>> balance
                            = directPaymentFeignClient.queryAccount(getPeopleInfoRequest);
                    if (balance.getCode() != 0) {
                        throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR,  "查询个账失败：" + balance.getMessage());
                    }
                    log.info("从直付获得{}个账额度: {}", balance.getData().get(0).getAccountSlipCode(), balance.getData().get(0).getAccountCurrAmt());

                    context.setDirectAmount(balance.getData().get(0).getAccountCurrAmt());
                    quotaDetailMap.put(QuotaControllerTypeEnum.DIRECT_PERSONAL.getCode(), balance.getData().get(0).getAccountCurrAmt());
                }
            }
        } else if (quotaController.getType().equals(QuotaControlSourceEnum.INSURER.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "保司三方控额暂不支持！");
        }


        //承担金额的总赔付额
        //先算出来，然后在实际理算时控额
        List<String> invoiceFeeHistoryPayoutList = adjustmentRecordList.stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid())).map(AdjustmentRecord::getFeePayoutList).toList();
        Map<String, BigDecimal> invoiceFeeHistoryPayoutMap = AdjustUtil.mergeJsonMaps(invoiceFeeHistoryPayoutList);
        context.setInvoiceFeeHistoryPayoutMap(invoiceFeeHistoryPayoutMap);

        //记录已确定的控额到上下文中
        context.setQuotaMap(quotaDetailMap);
        context.setAlarmQuotaMap(alarmQuotaMap);

        return quotaDetailMap.values().stream()
                .min(BigDecimal::compareTo)
                .map(min -> min.max(BigDecimal.ZERO))  // 将负数和0比较，取较大值
                .orElse(BigDecimal.ZERO);
    }


    /**
     * 制作额度字符串
     *
     * @param historyAmount 历史总共赔付额
     * @return
     */
    private BigDecimal calculateQuota(BigDecimal limit, BigDecimal historyAmount) {
        if (historyAmount.compareTo(limit) >= 0) {
            return BigDecimal.ZERO;
        }

        //计算新的额度
        BigDecimal newLimit = limit.subtract(historyAmount);

        return newLimit;
    }

}
