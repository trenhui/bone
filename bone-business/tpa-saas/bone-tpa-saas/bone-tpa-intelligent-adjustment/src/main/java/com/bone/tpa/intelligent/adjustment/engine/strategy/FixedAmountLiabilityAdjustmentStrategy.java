package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.core.exception.BizException;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.intelligent.adjustment.engine.constant.AdjustmentConstant;
import com.bone.tpa.intelligent.adjustment.engine.constant.FormulaConstant;
import com.bone.tpa.intelligent.adjustment.enums.CartesianFactorEnum;
import com.bone.tpa.intelligent.adjustment.enums.DeductEnum;
import com.bone.tpa.intelligent.adjustment.enums.LimitControlTypeEnum;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.intelligent.adjustment.enums.PayPercentTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaControlSourceEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.intelligent.adjustment.model.FormulaFactor;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.enums.PaymentBasisEnum;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * 定额给付型责任理算策略实现
 *
 */
@Service
public class FixedAmountLiabilityAdjustmentStrategy implements LiabilityAdjustmentStrategy {

    private static final int CALC_SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private  DeductCalculator deductCalculator;

    @Autowired
    private  CartesianProductGetter cartesianProductGetter;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    private final Logger logger = LoggerFactory.getLogger(FixedAmountLiabilityAdjustmentStrategy.class);


    @Override
    public void checkAdjustable(InvoiceAdjustmentContext context) {
        LiabilityConfig liability = context.getLiability();

        if (!liability.getLiabilityLimit().getType().equals(LimitControlTypeEnum.FIXED_AMOUNT.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误！控额方式错误！");
        }

        if (context.getLiability().getPaymentBasis() == null) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "该定额给付责任的给付条件为空！");
        }

        if (PaymentBasisEnum.SEVERE.getCode().equals(context.getLiability().getPaymentBasis())) {
            if (!AdjustmentConstant.TRUE.equals(context.getInvoice().getSevereFlag())) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "该发票非重疾，不符合责任给付条件！");
            }
        }

        if (liability.getPayPercent().getFactor().contains(CartesianFactorEnum.CLAIM_ACCUM.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误，定额给付不能设置赔案累计区间！");
        }
    }

    @Override
    public AdjustmentRecord adjustmentInvoice(InvoiceAdjustmentContext context) {
        try {
            logger.info("开始执行定额给付型理算，发票号：{}", context.getInvoice().getInvoiceNo());

            // 这是最后要记录到计算记录中的理算公式
            FormulaFactor formulaFactor = new FormulaFactor();

            // 提取必要的费用信息
            LiabilityConfig liability = context.getLiability();
            BigDecimal originAmount = liability.getLiabilityLimit().getFixedAmount();
            //如果这个金额算出来是负数就不对了
            if (originAmount.compareTo(BigDecimal.ZERO) < 0) {
                originAmount = BigDecimal.ZERO;
            }
            formulaFactor.setMoney(originAmount);

            // 计算免赔额, 发票费用层面
            BigDecimal deductAmount = deductCalculator.calculateDeductOnInvoice(context, originAmount);
            if (deductAmount.compareTo(BigDecimal.ZERO) >= 0) {
                logger.info("计算免赔额完成，免赔后剩余理算前金额：{}", originAmount.subtract(deductAmount));
                formulaFactor.setDeduct(deductAmount);
            }

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
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "该责任理算详情设置错误");
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

            // 计算理算后的赔付金额
            BigDecimal payoutAmount = originAmount.subtract(deductAmount).multiply(ratio).setScale(CALC_SCALE, ROUNDING_MODE);
            logger.info("计算理算后金额完成，金额：{}", payoutAmount);

            // 计算免赔额, 理算费用层面
            if (deductAmount.compareTo(BigDecimal.ZERO) == 0) {
                deductAmount = deductCalculator.calculateDeductOnAdjust(context, payoutAmount);
                if (deductAmount.compareTo(BigDecimal.ZERO) >= 0) {
                    payoutAmount = payoutAmount.subtract(deductAmount);
                    logger.info("计算免赔额完成，免赔后剩余控额前金额：{}", payoutAmount);
                    formulaFactor.setDeduct(deductAmount);
                }
            }

            if (context.getDeductRatio() != null) {
                formulaFactor.setDeduct(context.getDeductRatio());
            }

            // 责任维度的控额等于额度减去本责任前面的金额成分已经消耗的数值
            BigDecimal liabilityLimit = context.getLiabilityLimit().subtract(context.getLiabilityPayAmount());
            formulaFactor.setLimit(liabilityLimit);

            //合在一起就是本次最后的赔付额度
            BigDecimal liabilityPayAmount = payoutAmount;
            if (!context.getQuotaMap().isEmpty()) {
                liabilityPayAmount = AdjustUtil.findMin(payoutAmount, liabilityLimit);
            }
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
                    //.feePayoutList() 因为是定额给付，所以这里这个值是空的
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
                logger.warn("定额给付型理算失败，发票号：{}，错误信息：{}", context.getInvoice().getInvoiceNo(), ex.getMessage());
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
        //定额给付的公式根据免赔情况有所不同
        LiabilityDeduct liabilityDeduct = liability.getLiabilityDeduct();

        if (liabilityDeduct == null) {
            return FormulaConstant.FORMULA_FIXED;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
            return FormulaConstant.FORMULA_FIXED_DEDUCT_RATIO;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.MONEY.getCode())) {
            if (liabilityDeduct.getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
                return FormulaConstant.FORMULA_FIXED_DEDUCT_ADJUSTMENT;
            } else {
                return FormulaConstant.FORMULA_FIXED_DEDUCT_INVOICE;
            }
        }

        return FormulaConstant.FORMULA_FIXED;
    }


    /**
     * 生成数字公式
     *
     * 定额给付型理算只需要一个公式，不存在医疗报销型的多重组合内容。
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

        //如果没有控额就没有控额
        if (context.getQuotaMap().isEmpty()) {
            String moneyResult;
            if (liabilityDeduct == null || formulaFactor.getDeduct().compareTo(BigDecimal.ZERO) == 0) { // || formulaFactor.getLimit().compareTo(BigDecimal.ZERO) == 0) {
                //如果没有免赔，或者免赔额为零，或者限额为零，或者免赔方式是相对免赔且金额超出此金额，使用最基础的公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_FIXED_BASIC, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio());
            } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
                //如果是免赔比例，使用免赔比例公式。
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_FIXED_RATIO, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct());
            } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.INVOICE.getCode())) {
                //如果是免赔金额，且目标是发票金额，采用发票金额公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_FIXED_INVOICE, "给付额度", formulaFactor.getMoney(), formulaFactor.getDeduct(), formulaFactor.getRatio());
            } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
                //如果是免赔金额，且目标是理算金额，采用理算金额公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_FIXED_ADJUSTMENT, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct());
            } else {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "定额给付型责任不能使用天数免赔");
            }

            return String.format(FormulaConstant.EMPTY_HEAD, moneyResult, context.getLiabilityPayAmount());
        }


        String moneyResult;
        if (liabilityDeduct == null || formulaFactor.getDeduct().compareTo(BigDecimal.ZERO) == 0) { // || formulaFactor.getLimit().compareTo(BigDecimal.ZERO) == 0) {
            //如果没有免赔，或者免赔额为零，或者限额为零，或者免赔方式是相对免赔且金额超出此金额，使用最基础的公式
            moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_BASIC, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio(), "给付", formulaFactor.getLimit());
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
            //如果是免赔比例，使用免赔比例公式。
            moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_RATIO, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct(), "给付", formulaFactor.getLimit());
        } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.INVOICE.getCode())) {
            //如果是免赔金额，且目标是发票金额，采用发票金额公式
            moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_INVOICE, "给付额度", formulaFactor.getMoney(), formulaFactor.getDeduct(), formulaFactor.getRatio(), "给付", formulaFactor.getLimit());
        } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
            //如果是免赔金额，且目标是理算金额，采用理算金额公式
            moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_ADJUSTMENT, "给付额度", formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct(), "给付", formulaFactor.getLimit());
        } else {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "定额给付型责任不能使用天数免赔");
        }

        return String.format(FormulaConstant.EMPTY_HEAD, String.format(FormulaConstant.MIN_ONE, moneyResult), context.getLiabilityPayAmount());
    }


    @Override
    public LiabilityTypeEnum getSupportedType() {
        return LiabilityTypeEnum.FIXED_AMOUNT;
    }
}
