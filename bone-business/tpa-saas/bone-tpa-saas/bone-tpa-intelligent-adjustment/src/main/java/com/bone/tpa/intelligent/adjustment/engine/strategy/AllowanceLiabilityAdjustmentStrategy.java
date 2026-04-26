package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.core.exception.BizException;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.intelligent.adjustment.engine.constant.FormulaConstant;
import com.bone.tpa.intelligent.adjustment.enums.*;
import com.bone.tpa.intelligent.adjustment.model.FormulaFactor;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.enums.DateRangeEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaControlSourceEnum;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * 津贴给付型责任理算策略实现
 *
 */
@Service
public class AllowanceLiabilityAdjustmentStrategy implements LiabilityAdjustmentStrategy {

    private static final int CALC_SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private DeductCalculator deductCalculator;

    @Autowired
    private CartesianProductGetter cartesianProductGetter;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    private final Logger logger = LoggerFactory.getLogger(AllowanceLiabilityAdjustmentStrategy.class);


    @Override
    public void checkAdjustable(InvoiceAdjustmentContext context) {
        LiabilityConfig liability = context.getLiability();

        if (liability.getLiabilityLimit().getType().equals(LimitControlTypeEnum.FIXED_AMOUNT.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误！控额方式错误！");
        }

        //津贴给付型要额外检测津贴给付是否符合
        AllowanceDetail allowanceDetail = liability.getAllowanceDetail();
        if (allowanceDetail.getStartOutPeriod().equals(AllowanceStartOutPeriodEnum.DISALLOW.getCode())) {
            if (context.getInvoice().getVisitDate().after(context.getPolicyEndDate()) || context.getInvoice().getVisitDate().before(context.getPolicyStartDate())) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "该发票日期在保单有效期之外: " + context.getInvoice().getInvoiceNo());
            }
        }

        if (liability.getLiabilityDeduct() != null) {
            if (!DeductEnum.DAYS.getCode().equals(liability.getLiabilityDeduct().getDeductMode())) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误！津贴型免赔方式错误！");
            }
        }

        if (liability.getPayPercent().getFactor().contains(CartesianFactorEnum.CLAIM_ACCUM.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误，津贴给付不能设置赔案累计区间！");
        }
    }

    @Override
    public AdjustmentRecord adjustmentInvoice(InvoiceAdjustmentContext context) {
        try {
            logger.info("开始执行津贴给付型理算，发票号：{}", context.getInvoice().getInvoiceNo());
            LiabilityConfig liability = context.getLiability();

            Date policyEndDate = context.getPolicyEndDate();

            // 这是最后要记录到计算记录中的理算公式
            FormulaFactor formulaFactor = new FormulaFactor();

            AllowanceDetail allowanceDetail = liability.getAllowanceDetail();
            DateRangeEnum dateRangeEnum = DateRangeEnum.getByCode(allowanceDetail.getDayCountOption());

            Date[] invoiceDates = parse(context.getInvoice().getHospitalPeriod());

            // 先计算一共有几天。
            long invoiceTotalDays = DateUtil.calculateDayDifference(invoiceDates[0], invoiceDates[1], dateRangeEnum);
            long inPeriodDays = invoiceTotalDays;
            long outPeriodDays = 0;

            if (invoiceDates[0].after(policyEndDate)) {
                inPeriodDays = 0;
            } else if (invoiceDates[1].after(policyEndDate)) {
                inPeriodDays = DateUtil.calculateDayDifference(invoiceDates[0], policyEndDate, dateRangeEnum);
            }

            outPeriodDays = invoiceTotalDays - inPeriodDays;

            // 如果大于设置的上限，就减少到上限
            if (inPeriodDays > allowanceDetail.getInPeriodLimit()) {
                inPeriodDays = allowanceDetail.getInPeriodLimit();
            }
            if (outPeriodDays > allowanceDetail.getOutPeriodLimit()) {
                outPeriodDays = allowanceDetail.getOutPeriodLimit();
            }

            long totalDays = inPeriodDays + outPeriodDays;
            formulaFactor.setAllowanceDays(totalDays);
            logger.info("计算天数完成，天数：{}", totalDays);

            // 计算免赔天数
            Integer deductDays = deductCalculator.calculateDeductDaysForAllowance(context, totalDays);
            formulaFactor.setDeductDays(deductDays);
            context.setLiabilityDeductDays(deductDays);
            logger.info("计算免赔天数完成，免赔天数：{}", deductDays);

            // 取出该发票对应的赔付比例
            List<String> ratioFactorList = new ArrayList<>();
            Map<String, List<RangeObject>> ratioRangeMap = new HashMap<>();
            if (liability.getPayPercent().getType().equals(PayPercentTypeEnum.DIFFERENT.getCode())) {
                ratioFactorList = liability.getPayPercent().getFactor();
                ratioRangeMap = liability.getPayPercent().getRangeMap();
            }

            // 取出理算详情，只能有一条
            List<AdjustmentDetail> adjustmentDetailList = liability.getAdjustmentDetail();
            if (adjustmentDetailList.size() != 1) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "理算错误, 该责任理算详情缺失" + liability.getUuid());
            }
            AdjustmentDetail adjustmentDetail = adjustmentDetailList.get(0);

            // 获取赔付比例
            BigDecimal payRatio = cartesianProductGetter.valueGetter(context, ratioFactorList, ratioRangeMap, adjustmentDetail.getValueList());
            logger.info("获取赔付比例完成，赔付比例：{}", payRatio);
            formulaFactor.setRatio(payRatio);
            Map<String, BigDecimal> ratioPayoutMap = new HashMap<>();
            ratioPayoutMap.put("定额赔付比例", payRatio);

            // 转换为精确计算
            BigDecimal ratio = payRatio.divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP); // 保留4位小数避免精度丢失

            // 计算一天能赔付多少钱
            BigDecimal payoutPerDay = allowanceDetail.getAllowancePerDay().multiply(ratio).setScale(CALC_SCALE, ROUNDING_MODE);
            //如果这个金额算出来是负数就不对了
            if (payoutPerDay.compareTo(BigDecimal.ZERO) < 0) {
                payoutPerDay = BigDecimal.ZERO;
            }

            // 这个时候就可以计算赔付了
            BigDecimal payoutAmount = payoutPerDay.multiply(BigDecimal.valueOf(totalDays - deductDays));
            formulaFactor.setMoney(allowanceDetail.getAllowancePerDay());
            logger.info("计算津贴给付金额完成，金额：{}", payoutAmount);

            // 同时计算免赔额
            BigDecimal deductAmount = payoutPerDay.multiply(BigDecimal.valueOf(deductDays));

            // 计算免赔额。定额给付的免赔不可能是对发票金额，因此无需检测金额类型
//            BigDecimal deductAmount = deductCalculator.calculateAmount(context, payoutAmount);
//            if (deductAmount.compareTo(BigDecimal.ZERO) >= 0) {
//                payoutAmount = payoutAmount.subtract(deductAmount);
//                logger.debug("计算免赔额完成，免赔后剩余控额前金额：{}", payoutAmount);
//                formulaFactor.setDeduct(deductAmount);
//            }
//
//            if (context.getDeductRatio() != null) {
//                formulaFactor.setDeduct(context.getDeductRatio());
//            }

            // 责任维度的控额等于额度减去本责任前面的金额成分已经消耗的数值
            BigDecimal liabilityLimit = context.getLiabilityLimit().subtract(context.getLiabilityPayAmount());
            formulaFactor.setLimit(liabilityLimit);

            //合在一起就是本次最后的赔付额度
            BigDecimal liabilityPayAmount = AdjustUtil.findMin(payoutAmount, liabilityLimit);
            logger.info("计算责任保额完成，责任保额：{}", liabilityPayAmount);

            // 将本金额成分的赔付额和免赔额记录到上下文中
            context.setLiabilityPayAmount(context.getLiabilityPayAmount().add(liabilityPayAmount));
            context.setLiabilityDeductAmount(context.getLiabilityDeductAmount().add(deductAmount));

            //所有金额成分的理算全部完成以后，构造理算记录并且准备起来
            AdjustmentRecord adjustmentRecord = AdjustmentRecord.builder()
                    //发票相关信息
                    .invoiceId(context.getInvoice().getId())
                    .invoiceNo(context.getInvoice().getInvoiceNo())
                    .visitType(context.getInvoice().getVisitType())
                    .invoiceDate(context.getInvoice().getInvoiceDate())
                    .visitDate(context.getInvoice().getVisitDate())
                    .hospitalName(context.getInvoice().getHospitalName())
                    .hospitalDepartment(context.getInvoice().getHospitalDepartmentName())
                    .diagnosis(context.getInvoice().getDiagnosisCn())
                    .invoiceAmount(context.getInvoice().getTotalAmount())
                    //赔案相关信息
                    .relatedId(context.getClaim().getId())
                    .outInsureTime(context.getClaim().getOutInsureTime())
                    //责任相关信息
                    .policyNo(context.getPolicy().getPolicyNo())
                    .planUuid(context.getPlan().getUuid())
                    .coverageName(context.getCoverage().getCoverageName())
                    .liabilityUuid(liability.getUuid())
                    .liabilityName(context.getRecordName())
                    .version(liability.getVersion())
                    .accountType(liability.getAccountType())
                    //出险人信息
                    .insuredName(context.getOutInsure().getName())
                    .insuredCertificateType(context.getOutInsure().getIdentityType())
                    .insuredCertificateNumber(context.getOutInsure().getIdentityNo())
                    //发票上的信息
                    .invoiceHasYb(context.getInvoice().getHasYb())
                    .invoiceHospitalLevel(context.getInvoice().getHospitalLevel())
                    .invoiceHospitalType(context.getInvoice().getHospitalType())
                    //免赔相关信息
                    .deductType("无")
                    .deductAmount(context.getLiabilityDeductAmount())
                    .deductDays(context.getLiabilityDeductDays())
                    //控额
                    .quotaType(liability.getLiabilityLimit().getType())
                    .quotaDetail(adjustmentRecordService.quotaMapTransfer(context))
                    .ratioPayoutList(AdjustUtil.mapToString(ratioPayoutMap))
                    //公式
                    .formula(buildNumericFormula(context, formulaFactor))
                    //状态
                    .recordStatus(QuotaStatusEnum.FROZEN.getCode())
                    //赔付额，包括总体和单独
                    .payoutAmount(context.getLiabilityPayAmount()) //记录的总共赔付金额
                    //.feePayoutList() 津贴给付不需要
                    //结论
                    //.resultDetail(context.getLiabilityFeeMap().toString())
                    .adjustmentResult("理算成功")
                    //操作人
                    .operatorName(BizContextUtils.getUser())
                    .build();

            //如果有免赔才需要填入免赔相关的数据
            if (context.getLiabilityDeductAmount().compareTo(BigDecimal.ZERO) > 0) {
                adjustmentRecord.setDeductType(DeductEnum.ABSOLUTE.getValue());
                adjustmentRecord.setRemainDeductLimit(context.getRemainingLiabilityDeductLimit().toString());
            }

            //如果是公账就填入公账保单号
            if (liability.getQuotaController().getType().equals(QuotaControlSourceEnum.DIRECT.getCode())) {
                adjustmentRecord.setPublicAmountPolicyNo(liability.getQuotaController().getPolicyNo());
            }

            adjustmentRecord.setBizIdentityCode(context.getClaim().getBizIdentityCode());
            adjustmentRecord.setTenantId(context.getClaim().getTenantId());

            //警告额度
            String alarm = adjustmentRecordService.alarmQuotaProcessor(context);
            if (alarm != null) {
                adjustmentRecord.setAdjustmentResult("理算成功\n" + alarm);
            }

            // 返回理算结果
            return adjustmentRecord;
        } catch (BizException ex) {
            if (Integer.valueOf(ex.getCode()).equals(BizErrorCode.ADJUST_NOT_ALLOWED.getCode())) {
                // 记录错误日志并返回错误结果
                logger.warn("津贴给付型理算失败，发票号：{}，错误信息：{}", context.getInvoice().getInvoiceNo(), ex.getMessage());
                AdjustmentRecord adjustmentRecord = adjustmentRecordService.failRecordBuilder(context, ex.getMessage());

                return adjustmentRecord;
            }
            throw ex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String buildAdjustFormula(LiabilityConfig liability) {
        //津贴给付的公式根据免赔情况有所不同
        LiabilityDeduct liabilityDeduct = liability.getLiabilityDeduct();

        if (liabilityDeduct == null) {
            return FormulaConstant.FORMULA_ALLOWANCE;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.DAYS.getCode())) {
            return FormulaConstant.FORMULA_ALLOWANCE_DEDUCT_DAYS;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
            return FormulaConstant.FORMULA_ALLOWANCE_DEDUCT_RATIO;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.MONEY.getCode())) {
            return FormulaConstant.FORMULA_ALLOWANCE_DEDUCT_MONEY;
        }

        return FormulaConstant.FORMULA_ALLOWANCE;
    }



    /**
     * 生成数字公式
     *
     * 津贴给付型理算只需要一个公式，不存在医疗报销型的多重组合内容。
     *
     * @param context
     * @param formulaFactor
     * @return
     */
    private String buildNumericFormula(InvoiceAdjustmentContext context, FormulaFactor formulaFactor) {
        context.twoDecimalsFormatter();

        //如果啥都没有就直接回
        if (formulaFactor == null) {
            return "";
        }
        formulaFactor.twoDecimalsFormatter();

        //检查免赔类型
        LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();

        //构造一个每日津贴的字符串
        String allowancePerDay = String.format(FormulaConstant.EMPTY_FORMULA_ALLOWANCE_PER_DAY, formulaFactor.getMoney(), formulaFactor.getRatio());

        //构造一个津贴天数

        String dayCount;
        if (liabilityDeduct == null || formulaFactor.getDeductDays() == 0) {
            //没有免赔或者没有免赔天数就普通
            dayCount = String.format(FormulaConstant.EMPTY_FORMULA_ALLOWANCE_DAYS, formulaFactor.getAllowanceDays());
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.DAYS.getCode())) {
            //免赔天数就构造一个
            dayCount = String.format(FormulaConstant.EMPTY_FORMULA_ALLOWANCE_DEDUCT_DAYS, formulaFactor.getAllowanceDays(), formulaFactor.getDeductDays());
        } else {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "未知的免赔类型！");
        }

        return String.format(FormulaConstant.EMPTY_HEAD, String.format(FormulaConstant.EMPTY_FORMULA_ALLOWANCE, allowancePerDay, dayCount, formulaFactor.getLimit()), context.getLiabilityPayAmount());
    }


    @Override
    public LiabilityTypeEnum getSupportedType() {
        return LiabilityTypeEnum.ALLOWANCE;
    }


    /**
     * 解析逗号分隔的日期字符串为 Date 数组
     * @param input 输入字符串（格式：date1,date2）
     * @return 包含两个 Date 的数组
     * @throws IllegalArgumentException 输入不符合要求时抛出
     */
    public Date[] parse(String input) throws ParseException {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 空值检查
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("输入字符串不能为空");
        }

        // 分割字符串
        String[] parts = input.split("\\s*,\\s*"); // 允许逗号前后有空格
        if (parts.length != 2) {
            throw new IllegalArgumentException("输入必须包含两个日期，实际数量: " + parts.length);
        }

        return new Date[] {dateFormat.parse(parts[0]), dateFormat.parse(parts[1])};
    }
}
