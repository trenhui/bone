package com.bone.tpa.test.adjust;

import com.bone.tpa.adjustment.adapter.AdjustmentController;
import com.bone.tpa.claim.adapter.ClaimController;
import com.bone.tpa.claim.adapter.GenericQueryController;
import com.bone.tpa.intelligent.adjustment.engine.strategy.LiabilityAdjustmentStrategyFactory;
import com.bone.tpa.intelligent.adjustment.enums.InvoiceFeeTypeSourceEnum;
import com.bone.tpa.intelligent.adjustment.enums.LimitControlTypeEnum;
import com.bone.tpa.intelligent.adjustment.enums.PayPercentTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.AccountTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.sdk.dao.impl.CoverageRepository;
import com.bone.tpa.sdk.dao.impl.LiabilityRepository;
import com.bone.tpa.sdk.dao.impl.PlanRepository;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.sdk.service.LiabilityConverter;
import com.bone.tpa.test.BaseTest;
import com.bone.tpa.test.adjust.tpaAdjustData.*;
import freemarker.template.SimpleDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.Rollback;

import java.io.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class AdjustTest extends BaseTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String appCode = "tpa";

    @Autowired
    private ClaimController claimController;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private CoverageRepository coverageRepository;

    @Autowired
    private LiabilityRepository liabilityRepository;

    @Autowired
    private LiabilityConverter liabilityConverter;

    @Autowired
    private AdjustmentController adjustmentController;

    @Autowired
    private GenericQueryController genericQueryController;

    @Autowired
    private LiabilityAdjustmentStrategyFactory strategyFactory;

    String filePath = "src/test/java/com/bone/tpa/test/adjust/tpaAdjustData/file/";

    List<String> feeTypeNameList = List.of("发票总费用", "基金总支付", "基本统筹金额", "其他基金金额", "统筹起付线", "统筹赔付比例",
            "自付一", "自付二", "总自费", "丙类自费", "超限价自付", "三方已赔");


    @Test
    @Rollback(value = false)
    public void tpaLiabilityConvertor() {
        List<TpaCoverage> tpaCoverages = new ArrayList<>();
        List<TpaLiability> tpaLiabilities = new ArrayList<>();
        List<TpaLiabilityConfig> tpaLiabilityConfigs = new ArrayList<>();
        List<ValidRate> validRates = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            //保单
            Policy policy = new Policy();
            policy.setPolicyNo("testCasePolicy");
            policy.setEffdate(sdf.parse("2000-01-01 00:00:00"));
            policy.setExpdate(sdf.parse("2999-12-31 23:59:59"));
            policy.setTenantId(12345678L);
            policy.setInsureName("上海核工程研究设计院");
            policy.setInsureName("新永诚财产保险股份有限公司");
            policy.setConfigStatus("ACTIVE");
            policyRepository.insert(policy);

            //计划
            Plan plan = new Plan();
            plan.setId(1L);
            plan.setPolicyNo("testCasePolicy");
            plan.setUuid("uuid-testCasePlan");
            plan.setPlanName("testCasePlanName");
            plan.setPlanCode("testCasePlanCode");
            plan.setPlanLimit(BigDecimal.valueOf(-1)); // 计划不限额
            plan.setVersion("2025123101");
            plan.setStatus("ACTIVE");
            plan.setTenantId(12345678L);
            planRepository.insert(plan);

            //险种，这里开始要取数据了
            tpaCoverages = TpaSqlParser.readCoverageFromFile(filePath + "coverage.txt");
            List<Coverage> coverageList = new ArrayList<>();
            Long i = 1L;
            Map<String, Long> coverageIdMap = new HashMap<>();
            for (TpaCoverage tpaCoverage : tpaCoverages) {
                Coverage coverage = new Coverage();
                coverage.setId(i);
                coverageIdMap.put(tpaCoverage.getId(), i);
                i++;
                coverage.setPlanId(1L);
                coverage.setPolicyNo("testCasePolicy");
                coverage.setVersion("2025123101");
                coverage.setCoverageCode(tpaCoverage.getInsuranceCode());
                coverage.setCoverageName(tpaCoverage.getInsuranceName());
                coverage.setCoverageLimit(tpaCoverage.getLimitAmount());

                coverageList.add(coverage);
            }
            coverageRepository.insertBatch(coverageList);

            //责任，造数据最困难
            tpaLiabilities = TpaSqlParser.readLiabilityFromFile(filePath + "liability.txt");
            tpaLiabilityConfigs = TpaSqlParser.readTpaLiabilityConfigFromFile(filePath + "liabilityConfig.txt");
            Map<String, TpaLiabilityConfig> tpaLiabilityConfigMap = tpaLiabilityConfigs.stream().collect(Collectors.toMap(TpaLiabilityConfig::getResponsibilityId, t->t));
            validRates = TpaSqlParser.readValidRateFromFile(filePath + "validRate.txt");
            Map<String, ValidRate> validRateMap = validRates.stream().collect(Collectors.toMap(ValidRate::getResponsibilityId, t->t));

            List<Liability> liabilityList = new ArrayList<>();
            int j = 1000;
            i = 1L;
            for (TpaLiability tpaLiability : tpaLiabilities) {
                TpaLiabilityConfig tpaLiabilityConfig = tpaLiabilityConfigMap.get(tpaLiability.getId());
                ValidRate validRate = validRateMap.get(tpaLiability.getId());

                LiabilityConfig liabilityConfig = new LiabilityConfig();

                liabilityConfig.setId(i);
                i++;
                liabilityConfig.setPolicyNo("testCasePolicy");
                liabilityConfig.setUuid(tpaLiability.getId());
                liabilityConfig.setPlanId(1L);
                liabilityConfig.setCoverageId(coverageIdMap.get(tpaLiability.getInsuranceId()));
                liabilityConfig.setLiabilityCode(tpaLiability.getReponsibilityCode());
                liabilityConfig.setLiabilityName(tpaLiability.getReponsibilityName());
                liabilityConfig.setLiabilityType(LiabilityTypeEnum.REIMBURSEMENT);
                liabilityConfig.setVersion("2025123101");
                //后付相关都不要
                liabilityConfig.setInvoiceRelateAble(true);
                //定额和津贴相关的不要
                //适用对象不要
                //适用限定不要
                RestrictOutInsure restrictOutInsure = new RestrictOutInsure();
                restrictOutInsure.setType(List.of("HOSPITAL")); //这个写死就好了
                restrictOutInsure.setVisitType(List.of("OUTPATIENT_EMERGENCY", "INPATIENT", "PHARMACY", "SPECIAL_CLINIC", "ACCIDENT", "OTHER"));
                if (tpaLiability.getMedicalInsuranceCondition().equals("1")) {
                    restrictOutInsure.setMedicalInsurance("AFTER");
                } else if (tpaLiability.getMedicalInsuranceCondition().equals("2")) {
                    restrictOutInsure.setMedicalInsurance("BEFORE");
                } else if (tpaLiability.getMedicalInsuranceCondition().equals("3")) {
                    restrictOutInsure.setMedicalInsurance("BOTH");
                }

                List<InvoiceFeeType> invoiceFeeTypeList = new ArrayList<>();

                for(String feeTypeName : feeTypeNameList) {
                    InvoiceFeeType invoiceFeeType = new InvoiceFeeType();
                    invoiceFeeType.setIsDefault(true);
                    invoiceFeeType.setFeeType(feeTypeName);
                    invoiceFeeType.setOpen(true);
                    invoiceFeeType.setSource(InvoiceFeeTypeSourceEnum.INPUT.getCode());

                    invoiceFeeTypeList.add(invoiceFeeType);
                }
                restrictOutInsure.setInvoiceFeeType(invoiceFeeTypeList);

                List<InvoiceFeeType> liabilityFeeTypeList = new ArrayList<>();
                //总自费
                InvoiceFeeType zzfFeeType = new InvoiceFeeType();
                zzfFeeType.setIsDefault(true);
                zzfFeeType.setFeeType("总自费");
                if (tpaLiabilityConfig.getBearOwnExpense().equals("0")) {
                    zzfFeeType.setOpen(false);
                } else {
                    zzfFeeType.setOpen(true);
                }
                zzfFeeType.setSource(InvoiceFeeTypeSourceEnum.INPUT.getCode());
                liabilityFeeTypeList.add(zzfFeeType);

                //自付二
                InvoiceFeeType zf2FeeType = new InvoiceFeeType();
                zf2FeeType.setIsDefault(true);
                zf2FeeType.setFeeType("自付二");
                if (tpaLiabilityConfig.getBearingClassB().equals("0")) {
                    zf2FeeType.setOpen(false);
                } else {
                    zf2FeeType.setOpen(true);
                }
                zf2FeeType.setSource(InvoiceFeeTypeSourceEnum.INPUT.getCode());
                liabilityFeeTypeList.add(zf2FeeType);

                //合理金额
                InvoiceFeeType hljeFeeType = new InvoiceFeeType();
                hljeFeeType.setIsDefault(true);
                hljeFeeType.setFeeType("合理金额");
                if (tpaLiabilityConfig.getBearRational().equals("0")) {
                    hljeFeeType.setOpen(false);
                } else {
                    hljeFeeType.setOpen(true);
                }
                hljeFeeType.setSource(InvoiceFeeTypeSourceEnum.CALCULATE.getCode());
                hljeFeeType.setCalculateFormula("合理金额=发票总费用-基金总支付-自付二-总自费-三方已赔-不合理金额");
                liabilityFeeTypeList.add(hljeFeeType);

                restrictOutInsure.setLiabilityFeeType(liabilityFeeTypeList);

                liabilityConfig.setRestrictOutInsure(restrictOutInsure);

                //等待期不要
                liabilityConfig.setWaitingPeriod(-1);

                //赔付比例
                PayPercent payPercent = new PayPercent();
                payPercent.setType(PayPercentTypeEnum.SAME.getCode());
                payPercent.setPercent(100);
                liabilityConfig.setPayPercent(payPercent);

                //责任免赔 todo 偷懒
//                LiabilityDeduct liabilityDeduct = new LiabilityDeduct();
//                liabilityDeduct.setDeductMode(DeductEnum.MONEY.getCode());
//

                //次日限定 todo 偷懒


                //控额方
                QuotaController quotaController = new QuotaController();
                if (tpaLiability.getDeductionType().equals("01")) { // 直付
                    quotaController.setType("DIRECT");
                } else if (tpaLiability.getDeductionType().equals("06")) { // 这是其他保单
                    quotaController.setType("DIRECT");
                } else {
                    quotaController.setType("TPA");
                }

                liabilityConfig.setQuotaController(quotaController);

                //责任账户类型
                if (tpaLiability.getCorporateAccountControlAmount().compareTo(BigDecimal.ONE) == 0) {
                    liabilityConfig.setAccountType(AccountTypeEnum.PUBLIC.getCode());
                } else {
                    liabilityConfig.setAccountType(AccountTypeEnum.PERSONAL.getCode());
                }

                //保额类型不要
                liabilityConfig.setInsuranceQuotaType("PRESET");

                //责任额度
                LiabilityLimit liabilityLimit = new LiabilityLimit();
                if (tpaLiability.getDeductionType().equals("01")) { // 直付
                    liabilityLimit.setType(LimitControlTypeEnum.LIABILITY.getCode());
                } else if (tpaLiability.getDeductionType().equals("02")) {
                    liabilityLimit.setType(LimitControlTypeEnum.LIABILITY.getCode());
                } else if (tpaLiability.getDeductionType().equals("03")) { //暂时不要了
                    liabilityLimit.setType(LimitControlTypeEnum.PERSONAL.getCode());
                } else if (tpaLiability.getDeductionType().equals("04")) {
                    liabilityLimit.setType(LimitControlTypeEnum.LIABILITY_PERSONAL.getCode());
                } else if (tpaLiability.getDeductionType().equals("05")) {
                    liabilityLimit.setType(LimitControlTypeEnum.LIABILITY_VISIT.getCode());
                } else if (tpaLiability.getDeductionType().equals("06")) { // 直付
                    liabilityLimit.setType(LimitControlTypeEnum.LIABILITY.getCode());
                } else if (tpaLiability.getDeductionType().equals("07")) { // 不清楚是什么意思
                }
                liabilityLimit.setLiabilityLimit(tpaLiability.getReponsibilityLimit());

                liabilityConfig.setLiabilityLimit(liabilityLimit);

                //理算信息
                List<AdjustmentDetail> adjustmentDetailList = new ArrayList<>();

                Boolean needYb = false;
                Boolean notNeedYb = false;
                if (tpaLiability.getMedicalInsuranceCondition().equals("1")) {
                    needYb = true;
                } else if (tpaLiability.getMedicalInsuranceCondition().equals("2")) {
                    notNeedYb = true;
                } else if (tpaLiability.getMedicalInsuranceCondition().equals("3")) {
                    needYb = true;
                    notNeedYb = true;
                }

                //总自费
                if (!tpaLiabilityConfig.getBearOwnExpense().equals("0")) {
                    if (needYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("true");
                        adjustmentDetail.setLiabilityFeeType("总自费");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getOwnExpenseLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", tpaLiabilityConfig.getOwnExpenseRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                    if (notNeedYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("false");
                        adjustmentDetail.setLiabilityFeeType("总自费");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getOwnExpenseLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", tpaLiabilityConfig.getOwnExpenseRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                }

                //自付二
                if (!tpaLiabilityConfig.getBearingClassB().equals("0")) {
                    if (needYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("true");
                        adjustmentDetail.setLiabilityFeeType("自付二");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getOwnExpenseClassBLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", tpaLiabilityConfig.getOwnExpenseClassBRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                    if (notNeedYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("false");
                        adjustmentDetail.setLiabilityFeeType("自付二");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getOwnExpenseClassBLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", tpaLiabilityConfig.getOwnExpenseClassBRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                }

                //合理金额
                if (!tpaLiabilityConfig.getBearRational().equals("0")) {
                    if (needYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("true");
                        adjustmentDetail.setLiabilityFeeType("合理金额");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getRationalLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", validRate.getRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                    if (notNeedYb) {
                        AdjustmentDetail adjustmentDetail = new AdjustmentDetail();
                        adjustmentDetail.setHasYb("false");
                        adjustmentDetail.setLiabilityFeeType("合理金额");
                        adjustmentDetail.setLiabilityLimit(tpaLiabilityConfig.getRationalLimitAmount());
                        Map<String, BigDecimal> valueList = new HashMap<>();
                        valueList.put("DEFAULT", validRate.getRate());

                        adjustmentDetail.setValueList(valueList);

                        adjustmentDetailList.add(adjustmentDetail);
                    }
                }

                liabilityConfig.setAdjustmentDetail(adjustmentDetailList);

                liabilityConfig.setFormula(strategyFactory.getLiabilityAdjudicationStrategy(liabilityConfig.getLiabilityType()).buildAdjustFormula(liabilityConfig));

                liabilityList.add(liabilityConverter.toLiability(liabilityConfig));
            }
            liabilityRepository.insertBatch(liabilityList);

            //准备赔案和发票
            System.out.println("责任生成完成");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Rollback(value = false)
    public void liabilityConvertor() {

    }
}
