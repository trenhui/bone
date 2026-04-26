package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.core.util.BizContextUtils;
import com.bone.tpa.intelligent.adjustment.engine.constant.AdjustmentConstant;
import com.bone.tpa.intelligent.adjustment.engine.constant.FormulaConstant;
import com.bone.tpa.intelligent.adjustment.enums.*;
import com.bone.tpa.intelligent.adjustment.model.FormulaFactor;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.enums.AdjustmentDetailFailReasonEnum;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaControlSourceEnum;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.adjustment.model.liability.*;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * 医疗报销型责任理算策略实现
 *
 */
@Slf4j
@Service
public class ReimbursementLiabilityAdjustmentStrategy implements LiabilityAdjustmentStrategy {

    private static final int CALC_SCALE = 6; // 精确计算位数
    private static final int RESULT_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP; // 四舍五入模式

    @Autowired
    private DeductCalculator deductCalculator;

    @Autowired
    private InvoiceFeeCalculator invoiceFeeCalculator;

    @Autowired
    private CartesianProductGetter cartesianProductGetter;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    private final Logger logger = LoggerFactory.getLogger(ReimbursementLiabilityAdjustmentStrategy.class);


    /**
     * 费用报销型理算的理算顺序
     */
    private List<String> specificOrder = Arrays.asList("合理金额", "自付二", "总自费", "丙类自费", "超限价自付");

    @Override
    public void checkAdjustable(InvoiceAdjustmentContext context) {
        LiabilityConfig liability = context.getLiability();
        ClaimInvoice invoice = context.getInvoice();

        if (liability.getLiabilityLimit().getType().equals(LimitControlTypeEnum.FIXED_AMOUNT.getCode())) {
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任设置错误！控额方式错误！");
        }

        //1.3 检查适用出险
        RestrictOutInsure restrictOutInsure = liability.getRestrictOutInsure();
        if (restrictOutInsure.getMedicalInsurance().equals(MedicalInsuranceConditionEnum.AFTER.getCode())) {
            if (!invoice.getHasYb().equals(AdjustmentConstant.TRUE)) {
                log.error("强制使用医保的责任，未使用医保卡进行就诊" + invoice.getInvoiceNo());
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, AdjustmentDetailFailReasonEnum.EIGHT.getDesc());
            }
        }
        if (restrictOutInsure.getMedicalInsurance().equals(MedicalInsuranceConditionEnum.BEFORE.getCode())) {
            if (!invoice.getHasYb().equals(AdjustmentConstant.FALSE)) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "仅未使用医保才能赔付");
            }
        }

        //检查就诊类型
        Boolean visitFlag = false;
        for (String visitType : restrictOutInsure.getVisitType()) {
            if (VisitTypeEnum.fromCode(visitType).getValue().equals(invoice.getVisitTypeCn())) {
                visitFlag = true;
            }
        }
        if (!visitFlag) {
            log.error("该责任不支持此就诊类型: " + invoice.getVisitTypeCn());
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, AdjustmentDetailFailReasonEnum.TEN.getDesc());
        }
    }

    @Override
    public AdjustmentRecord adjustmentInvoice(InvoiceAdjustmentContext context) {
        //到这里的时候检验已经过了，直接进入理算阶段
        try {
            logger.info("开始执行费用报销型理算，发票号：{}", context.getInvoice().getInvoiceNo());

            LiabilityConfig liability = context.getLiability();

            // 这是最后要记录到计算记录中的理算公式
            List<FormulaFactor> formulaFactorList = new ArrayList<>();

            // 提取被承担的费用信息
            List<InvoiceFeeType> invoiceFeeTypeList =  liability.getRestrictOutInsure().getLiabilityFeeType().stream().filter(InvoiceFeeType::getOpen).collect(Collectors.toList());
            if (invoiceFeeTypeList.isEmpty()) {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "理算错误, 该责任没有承担费用" + liability.getUuid());
            }

            // 进行排序
            Map<String, Integer> orderMap = new HashMap<>();
            for (int i = 0; i < specificOrder.size(); i++) {
                orderMap.put(specificOrder.get(i), i);
            }

            invoiceFeeTypeList.sort(Comparator.comparing(item ->
                    orderMap.getOrDefault(item.getFeeType(), Integer.MAX_VALUE)));

            Map<String, BigDecimal> feeCalculateMap = new HashMap<>();
            Map<String, BigDecimal> ratioPayoutMap = new HashMap<>();
            Map<String, BigDecimal> feePayoutMap = new HashMap<>();
            Map<String, BigDecimal> feeDeductMap = new HashMap<>();
            Map<String, BigDecimal> feeOccupyMap = new HashMap<>();

            int i = 0;

            // 针对每一个被承担的费用信息，开始理算
            for (InvoiceFeeType invoiceFeeType : invoiceFeeTypeList) {
                context.setCurrentFeeType(invoiceFeeType);

                //公式构建
                FormulaFactor formulaFactor = new FormulaFactor();
                formulaFactor.setFeeType(invoiceFeeType.getFeeType());

                //计算发票上的总金额。先从上下文里取是否已经有过，没有的话再算
                BigDecimal originAmount = context.getLiabilityFeeMap().get(invoiceFeeType.getFeeType());
                if (originAmount == null) {
                    originAmount = invoiceFeeCalculator.calculateInvoiceFee(context, invoiceFeeType);
                }

                //如果这个金额算出来是负数就不对了
                if (originAmount.compareTo(BigDecimal.ZERO) < 0) {
                    originAmount = BigDecimal.ZERO;
                    logger.warn("金额成分{}的剩余理算前金额小于零", invoiceFeeType.getFeeType());
                }

                BigDecimal currentAmount = originAmount;
                //此处存在一个特殊情况，需要判断后付责任相关的问题
                //如果是后付责任就要通过差值计算当前金额
                if (context.getLiabilityFeeOccupyDifferenceMap().get(invoiceFeeType.getFeeType()) != null) {
                    currentAmount = originAmount.subtract(context.getLiabilityFeeOccupyDifferenceMap().get(invoiceFeeType.getFeeType()));
                }

                logger.info("计算发票金额完成。金额成分{}，剩余理算前金额：{}", invoiceFeeType.getFeeType(), currentAmount);
                context.setCurrentInvoiceFeeAmount(currentAmount);
                formulaFactor.setMoney(currentAmount);

                // 计算免赔额, 发票费用层面
                BigDecimal deductAmount = deductCalculator.calculateDeductOnInvoice(context, currentAmount);
                if (deductAmount.compareTo(BigDecimal.ZERO) >= 0) {
                    logger.info("计算免赔额完成，免赔后剩余理算前金额：{}", currentAmount.subtract(deductAmount));
                    formulaFactor.setDeduct(deductAmount);
                    feeDeductMap.put(invoiceFeeType.getFeeType(), deductAmount);
                }

                // 筛选出该费用以及当前发票情况下对应的理算详情, 这个时候无论如何也只有一条的
                List<AdjustmentDetail> adjustmentDetailList = liability.getAdjustmentDetail().stream()
                        .filter(t -> t.getLiabilityFeeType().equals(invoiceFeeType.getFeeType())).toList();
                if (liability.getPayPercent().getType().equals(PayPercentTypeEnum.DIFFERENT.getCode()) &&
                        liability.getPayPercent().getFactor().contains(CartesianFactorEnum.MEDICAL_INSURANCE.getCode())) {
                    adjustmentDetailList = adjustmentDetailList.stream().filter(t -> context.getInvoice().getHasYb().equals(YbEnum.getByCode(t.getHasYb()).getValue())).toList();
                }

                if (adjustmentDetailList.size() != 1) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "理算错误, 该责任理算详情缺失: " + liability.getUuid());
                }
                AdjustmentDetail adjustmentDetail = adjustmentDetailList.get(0);

                // 取出该发票对应的赔付比例
                List<String> ratioFactorList = new ArrayList<>();
                Map<String, List<RangeObject>> ratioRangeMap = new HashMap<>();
                if (liability.getPayPercent().getType().equals(PayPercentTypeEnum.DIFFERENT.getCode())) {
                    ratioFactorList = liability.getPayPercent().getFactor();
                    ratioRangeMap = liability.getPayPercent().getRangeMap();
                }


                // 获取赔付比例
                BigDecimal payRatio = cartesianProductGetter.valueGetter(context, ratioFactorList, ratioRangeMap, adjustmentDetail.getValueList());
                logger.info("获取赔付比例完成，赔付比例：{}", payRatio);
                formulaFactor.setRatio(payRatio);

                // 转换为精确计算
                BigDecimal ratio = payRatio.divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP); // 保留4位小数避免精度丢失

                // 计算理算后的赔付金额
                BigDecimal payoutAmount = currentAmount.subtract(deductAmount).multiply(ratio).setScale(CALC_SCALE, ROUNDING_MODE);
                logger.info("计算理算后金额完成，金额：{}", payoutAmount);

                // 计算免赔额, 理算费用层面
                if (deductAmount.compareTo(BigDecimal.ZERO) == 0) {
                    deductAmount = deductCalculator.calculateDeductOnAdjust(context, payoutAmount);
                    if (deductAmount.compareTo(BigDecimal.ZERO) >= 0) {
                        payoutAmount = payoutAmount.subtract(deductAmount);
                        logger.info("计算免赔额完成，免赔后剩余控额前金额：{}", payoutAmount);
                        formulaFactor.setDeduct(deductAmount);
                        if (feeDeductMap.containsKey(invoiceFeeType.getFeeType())) {
                            feeDeductMap.replace(invoiceFeeType.getFeeType(), feeDeductMap.get(invoiceFeeType.getFeeType()).add(deductAmount));
                        } else {
                            feeDeductMap.put(invoiceFeeType.getFeeType(), deductAmount);
                        }
                    }
                }

                // 这里进行四舍五入
                payoutAmount = payoutAmount.setScale(RESULT_SCALE, ROUNDING_MODE);
                logger.info("四舍五入后，最终赔付额：{}", payoutAmount);

                // 记录控额之前的金额
                feeCalculateMap.put(invoiceFeeType.getFeeType(), payoutAmount);

                // 算上最后的发票费用金额类型控额
                // 责任维度的控额等于额度减去本责任前面的金额成分已经消耗的数值
                BigDecimal liabilityLimit = context.getLiabilityLimit().subtract(context.getLiabilityPayAmount());
                // 费用维度的控额等于理算信息中这个金额成分（和医保）设置的控额，再减去历史已经消耗的额度
                BigDecimal liabilityFeeLimit = adjustmentDetail.getLiabilityLimit();
                BigDecimal liabilityFeeHistoryAmount = context.getInvoiceFeeHistoryPayoutMap().get(invoiceFeeType.getFeeType());
                if (liabilityFeeHistoryAmount != null) {
                    liabilityFeeLimit = liabilityFeeLimit.subtract(liabilityFeeHistoryAmount);
                }
                if (liabilityFeeLimit.compareTo(BigDecimal.ZERO) < 0) {
                    liabilityFeeLimit = BigDecimal.ZERO;
                }

                context.getQuotaMap().put(QuotaControllerTypeEnum.FEE_TYPE.getCode() + i, liabilityFeeLimit);
                context.getFeeTypeList().add(invoiceFeeType.getFeeType());
                i++;
                formulaFactor.setLimit(liabilityFeeLimit);

                //合在一起就是本次最后的赔付额度
                BigDecimal liabilityPayAmount = AdjustUtil.findMin(payoutAmount, liabilityLimit, liabilityFeeLimit);
                logger.info("计算责任保额完成，责任保额：{}", liabilityPayAmount);
                //如果这里费用的额度用完了，去上下文里打一个标
                if (liabilityFeeLimit.compareTo(liabilityPayAmount) == 0) {
                    context.setNextLiabilityFlag(true);
                }
                //如果是被控额导致的赔付为0，本次免赔额也是0
//                if (liabilityLimit.compareTo(BigDecimal.ZERO) == 0 || liabilityFeeLimit.compareTo(BigDecimal.ZERO) == 0) {
//                    deductAmount = BigDecimal.ZERO;
//                }
                if (context.getDeductRatio() != null) {
                    formulaFactor.setDeduct(context.getDeductRatio());
                }

                //记录到承担责任费用的map中去
                //该金额计算部分，可能值得商榷 todo 存在一定的小数点问题
                BigDecimal occupyAmount = BigDecimal.ZERO;
                if (ratio.compareTo(BigDecimal.ZERO) > 0) {
                    occupyAmount = liabilityPayAmount.divide(ratio, ROUNDING_MODE);
                    if (occupyAmount.compareTo(currentAmount) > 0) {
                        occupyAmount = currentAmount;
                    }
                    ratioPayoutMap.put(invoiceFeeType.getFeeType(), payRatio);
                } else {
                    ratioPayoutMap.put(invoiceFeeType.getFeeType(), BigDecimal.ZERO);
                }

                //存入对应的数值存储map中，用于构建理算明细
                if (feePayoutMap.get(invoiceFeeType.getFeeType()) == null) {
                    feePayoutMap.put(invoiceFeeType.getFeeType(), liabilityPayAmount);
                    feeOccupyMap.put(invoiceFeeType.getFeeType(), occupyAmount);
                } else {
                    feePayoutMap.replace(invoiceFeeType.getFeeType(), feePayoutMap.get(invoiceFeeType.getFeeType()).add(liabilityPayAmount));
                    feeOccupyMap.replace(invoiceFeeType.getFeeType(), feeOccupyMap.get(invoiceFeeType.getFeeType()).add(occupyAmount));
                }

                //如果该赔案存在后付赔案，并且超出比例外不赔付，则存入对应的上下文map中，用于记录该发票总共的的 占据额-赔付额，用于后付责任计算
                if (NextLiabilityTypeEnum.EXCEEDING_NO_PAY.getCode().equals(liability.getNextLiabilityType())) {
                    if (context.getLiabilityFeeOccupyDifferenceMap().get(invoiceFeeType.getFeeType()) != null) {
                        context.getLiabilityFeeOccupyDifferenceMap().replace(invoiceFeeType.getFeeType(), occupyAmount.subtract(liabilityPayAmount).add(context.getLiabilityFeeOccupyDifferenceMap().get(invoiceFeeType.getFeeType())));
                    } else {
                        context.getLiabilityFeeOccupyDifferenceMap().put(invoiceFeeType.getFeeType(), occupyAmount.subtract(liabilityPayAmount));
                    }
                }

                // 将本金额成分的赔付额和免赔额记录到上下文中
                context.setLiabilityPayAmount(context.getLiabilityPayAmount().add(liabilityPayAmount));
                context.setLiabilityDeductAmount(context.getLiabilityDeductAmount().add(deductAmount));

                // 到这里该金额的理算已经完成，将其记录下来并且开始搞下一个金额成分
                context.getLiabilityFeeMap().put(invoiceFeeType.getFeeType(), originAmount.subtract(liabilityPayAmount));

                // 制作公式的参数
                formulaFactorList.add(formulaFactor);
            }

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
                    //公式
                    .formula(buildNumericFormula(context, formulaFactorList))
                    //状态
                    .recordStatus(QuotaStatusEnum.FROZEN.getCode())
                    //赔付额，包括总体和单独
                    .payoutAmount(context.getLiabilityPayAmount()) //记录的总共赔付金额
                    .feeCalculateList(AdjustUtil.mapToString(feeCalculateMap))
                    .ratioPayoutList(AdjustUtil.mapToString(ratioPayoutMap))
                    .feePayoutList(AdjustUtil.mapToString(feePayoutMap))
                    .feeDeductList(AdjustUtil.mapToString(feeDeductMap))
                    .feeOccupyList(AdjustUtil.mapToString(feeOccupyMap))
                    .feeRemainList(AdjustUtil.mapToString(context.getLiabilityFeeMap()))
                    //结论
                    //.resultDetail("")
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

        } catch (TpaBizException ex) {
            if (ex.getErrorCode().equals(BizErrorCode.ADJUST_NOT_ALLOWED)) {
                // 记录错误日志并返回错误结果
                logger.warn("费用报销型理算失败，发票号：{}，错误信息：{}", context.getInvoice().getInvoiceNo(), ex.getMessage());
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
        LiabilityDeduct liabilityDeduct = liability.getLiabilityDeduct();

        String formula = FormulaConstant.FORMULA_BASIC;;
        if (liabilityDeduct == null || DeductEnum.RELATIVE.getCode().equals(liabilityDeduct.getDeductType())) {
            //如果没有免赔，或者免赔方式是相对免赔，使用最基础的公式
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
            //如果是免赔比例，使用免赔比例公式。
            formula = FormulaConstant.FORMULA_DEDUCT_RATIO;
        } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.DAYS.getCode())) {
            //如果是免赔天数，报错
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "费用报销型责任不能使用天数免赔");
        } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.INVOICE.getCode())) {
            //如果是免赔金额，且目标是发票金额，采用发票金额公式
            formula = FormulaConstant.FORMULA_DEDUCT_INVOICE;
        } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
            //如果是免赔金额，且目标是理算金额，采用理算金额公式
            formula = FormulaConstant.FORMULA_DEDUCT_ADJUSTMENT;
        }

        String deductName = FormulaConstant.DEDUCT_MONEY;
        if (liabilityDeduct != null && liabilityDeduct.getFactor().contains(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
            deductName = FormulaConstant.DEDUCT_MONEY_DIVERSE_BY_FEE;
        }

        List<InvoiceFeeType> invoiceFeeTypeList =  liability.getRestrictOutInsure().getLiabilityFeeType().stream().filter(InvoiceFeeType::getOpen).toList();

        if (invoiceFeeTypeList.size() == 1) {
            //如果只有一个费用类型，这里就可以直接构造了
            String result = String.format(FormulaConstant.MIN_THREE, String.format(formula, invoiceFeeTypeList.get(0).getFeeType(), deductName), FormulaConstant.INVOICE_FEE_LIMIT, FormulaConstant.LIABILITY_LIMIT);
            return String.format(FormulaConstant.HEAD, result);
        }

        //如果不止一个费用类型，就分开构造
        List<String> resultList = new ArrayList<>();
        for (InvoiceFeeType invoiceFeeType : invoiceFeeTypeList) {
            resultList.add(String.format(FormulaConstant.MIN_TWO, String.format(formula, invoiceFeeType.getFeeType(), deductName), FormulaConstant.INVOICE_FEE_LIMIT));
        }

        return String.format(FormulaConstant.HEAD, String.format(FormulaConstant.MIN_TWO, String.join(FormulaConstant.PLUS, resultList), FormulaConstant.LIABILITY_LIMIT));
    }


    /**
     * 生成数字公式
     *
     * @param context
     * @param formulaFactorList
     * @return
     */
    private String buildNumericFormula(InvoiceAdjustmentContext context, List<FormulaFactor> formulaFactorList) {
        context.twoDecimalsFormatter();
        //如果啥都没有就直接回
        if (formulaFactorList == null || formulaFactorList.isEmpty()) {
            return "";
        }

        List<String> feeTypeResultList = new ArrayList<>();
        for (FormulaFactor formulaFactor : formulaFactorList) {
            formulaFactor.twoDecimalsFormatter();

            LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();

            String moneyResult;
            if (liabilityDeduct == null || formulaFactor.getDeduct().compareTo(BigDecimal.ZERO) == 0) { // || formulaFactor.getLimit().compareTo(BigDecimal.ZERO) == 0) {
                //如果没有免赔，或者免赔额为零，或者限额为零，或者免赔方式是相对免赔且金额超出此金额，使用最基础的公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_BASIC, formulaFactor.getFeeType(), formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getFeeType(), formulaFactor.getLimit());
            } else if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
                //如果是免赔比例，使用免赔比例公式。
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_RATIO, formulaFactor.getFeeType(), formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct(), formulaFactor.getFeeType(), formulaFactor.getLimit());
            } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.INVOICE.getCode())) {
                //如果是免赔金额，且目标是发票金额，采用发票金额公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_INVOICE, formulaFactor.getFeeType(), formulaFactor.getMoney(), formulaFactor.getDeduct(), formulaFactor.getRatio(), formulaFactor.getFeeType(), formulaFactor.getLimit());
            } else if (liabilityDeduct.getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
                //如果是免赔金额，且目标是理算金额，采用理算金额公式
                moneyResult = String.format(FormulaConstant.EMPTY_FORMULA_DEDUCT_ADJUSTMENT, formulaFactor.getFeeType(), formulaFactor.getMoney(), formulaFactor.getRatio(), formulaFactor.getDeduct(), formulaFactor.getFeeType(), formulaFactor.getLimit());
            } else{
                //如果是免赔天数，报错
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "费用报销型责任不能使用天数免赔");
            }

            feeTypeResultList.add(moneyResult);
        }

        //如果只有一种费用类型，就比较简单地完成
        if (feeTypeResultList.size() == 1) {
            return String.format(FormulaConstant.EMPTY_HEAD, String.format(FormulaConstant.MIN_TWO_WITH_NAME, feeTypeResultList.get(0), context.getLiabilityLimit()), context.getLiabilityPayAmount());
        }

        //如果不止一个费用类型，就分开构造
        List<String> resultList = new ArrayList<>();
        for (String moneyResult : feeTypeResultList) {
            resultList.add(String.format(FormulaConstant.MIN_ONE, moneyResult));
        }

        return String.format(FormulaConstant.EMPTY_HEAD, String.format(FormulaConstant.MIN_TWO_WITH_NAME, String.join(FormulaConstant.PLUS, resultList), context.getLiabilityLimit()), context.getLiabilityPayAmount());
    }


    @Override
    public LiabilityTypeEnum getSupportedType() {
        return LiabilityTypeEnum.REIMBURSEMENT;
    }
}
