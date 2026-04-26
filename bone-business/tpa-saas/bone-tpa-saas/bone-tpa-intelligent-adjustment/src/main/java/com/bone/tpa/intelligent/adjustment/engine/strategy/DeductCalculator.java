package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.tpa.intelligent.adjustment.enums.CartesianFactorEnum;
import com.bone.tpa.intelligent.adjustment.enums.DeductEnum;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceAdjustmentContext;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityDeduct;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.util.DateUtil;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 免赔额计算器，负责计算发票的免赔额。
 * 根据不同的免赔额类型（绝对免赔额、相对免赔额等），计算相应的免赔金额。
 */
@Component
@Slf4j
public class DeductCalculator {

    private final CartesianProductGetter cartesianProductGetter;


    private static final int CALC_SCALE = 6; // 精确计算位数
    private static final int RESULT_SCALE = 2;

    public DeductCalculator(CartesianProductGetter cartesianProductGetter) {
        this.cartesianProductGetter = cartesianProductGetter;
    }

    /**
     * 针对发票费用的免赔
     */
    public BigDecimal calculateDeductOnInvoice(InvoiceAdjustmentContext context, BigDecimal originValue) {
        if (context.getLiability().getLiabilityDeduct() == null) {
            return BigDecimal.ZERO;
        }
        if (context.getLiability().getLiabilityDeduct().getDeductTarget().equals(DeductEnum.INVOICE.getCode())) {
            return calculateAmount(context, originValue);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 针对理算金额的免赔
     */
    public BigDecimal calculateDeductOnAdjust(InvoiceAdjustmentContext context, BigDecimal originValue) {
        if (context.getLiability().getLiabilityDeduct() == null) {
            return BigDecimal.ZERO;
        }
        if (context.getLiability().getLiabilityDeduct().getDeductTarget().equals(DeductEnum.ADJUST.getCode())) {
            return calculateAmount(context, originValue);
        }

        return BigDecimal.ZERO;
    }


    /**
     * 计算免赔额
     *
     */
    public BigDecimal calculateAmount(InvoiceAdjustmentContext context, BigDecimal originValue) {
        if (context.getLiability().getLiabilityDeduct() == null) {
            return BigDecimal.ZERO;
        }

        LiabilityConfig liabilityConfig = context.getLiability();
        LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();
        //三种免赔类型的处理不同，分开操作
        if (liabilityDeduct.getDeductMode().equals(DeductEnum.DAYS.getCode())) {
            if (liabilityConfig.getLiabilityType().equals(LiabilityTypeEnum.ALLOWANCE)) {
                return BigDecimal.ZERO;
            }
            //免赔天数不应该走这里，
            throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "责任不允许设置免赔天数: " + context.getLiability().getLiabilityName());
        }

        //首先去valueList里面取得这条发票对应的数据
        BigDecimal value = cartesianProductGetter.valueGetter(context, liabilityDeduct.getFactor(), new HashMap<>(), liabilityDeduct.getValueList());

        if (liabilityDeduct.getDeductMode().equals(DeductEnum.RATIO.getCode())) {
            context.setRemainingLiabilityDeductLimit(BigDecimal.ZERO);
            context.setDeductRatio(value);

            // 转换为精确计算
            BigDecimal ratio = value.divide(BigDecimal.valueOf(100), CALC_SCALE, RoundingMode.HALF_UP); // 保留4位小数避免精度丢失

            //如果免赔比例大于100%就按照100%来
            if (ratio.compareTo(BigDecimal.ONE) > 0) {
                ratio = BigDecimal.ONE;
                context.setDeductRatio(new BigDecimal(100));
            }

            // 计算理算后的赔付金额
            BigDecimal deductAmount = originValue.multiply(ratio).setScale(RESULT_SCALE, RoundingMode.HALF_DOWN);

            return deductAmount;
        }

        if (liabilityDeduct.getDeductMode().equals(DeductEnum.MONEY.getCode())) {
            BigDecimal historyDeductValue = historyDeductCalculator(context, liabilityDeduct.getFactor());

            BigDecimal remainingValue = value.subtract(historyDeductValue).max(BigDecimal.ZERO);

            //记录到上下文中
            context.setRemainingLiabilityDeductLimit(remainingValue);

            //如果是相对免赔并且金额大于这个值，可以直接返回0
            if (DeductEnum.RELATIVE.getCode().equals(liabilityDeduct.getDeductType()) && originValue.compareTo(value) > 0) {
                return BigDecimal.ZERO;
            }

            //其余情况都是返回免赔金额和当前金额之间的小值
            BigDecimal deductAmount = originValue.compareTo(remainingValue) > 0 ? remainingValue : originValue;
            //用于标记本次已经用了多少额度。如果是根据赔付金额区分，那就不用记录因为永远是0
            if (!liabilityDeduct.getFactor().contains(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
                context.setCurrentDeductAmount(context.getCurrentDeductAmount().add(deductAmount));
            }

            return deductAmount;
        }

        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "理算错误, 该责任免赔设置错误" + context.getLiability().getLiabilityName());
    }


    /**
     * 计算津贴给付的免赔，这个位置返回的是免赔天数
     */
    public Integer calculateDeductDaysForAllowance(InvoiceAdjustmentContext context, long originDays) {
        LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();

        if (liabilityDeduct == null) {
            return 0;
        }

        //这里如果不是免赔天数就返回0
        if (!liabilityDeduct.getDeductMode().equals(DeductEnum.DAYS.getCode())) {
            return 0;
        }

        //首先去valueList里面取得这条发票对应的数据
        //因为是天数所以一定是整数
        Integer value = cartesianProductGetter.valueGetter(context, liabilityDeduct.getFactor(), new HashMap<>(), liabilityDeduct.getValueList()).intValue();

        //取出历史，
        Integer historyDeductDays = historyDeductDaysCalculator(context, liabilityDeduct.getFactor());

        //这个是还剩多少天可以免赔
        Integer remainingValue = value - historyDeductDays;

        if (remainingValue < 0) {
            remainingValue = 0;
        }

        if (originDays > remainingValue) {
            //用于标记本次已经用了多少额度。如果是根据赔付金额区分，那就不用记录因为永远是0
//            if (!liabilityDeduct.getFactor().contains(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
//                context.setCurrentDeductAmount(context.getCurrentDeductAmount().add(BigDecimal.valueOf(remainingValue)));
//            }

            return remainingValue;
        } else {
            //用于标记本次已经用了多少额度。如果是根据赔付金额区分，那就不用记录因为永远是0
//            if (!liabilityDeduct.getFactor().contains(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
//                context.setCurrentDeductAmount(context.getCurrentDeductAmount().add(BigDecimal.valueOf(originDays)));
//            }

            return Math.toIntExact(originDays);
        }
    }


    /**
     * 计算这个免赔历史已经用了多少额度
     *
     * 1. 如果是按次，问题转化为本次免赔用了多少额度
     * 2. 如果是按年度，从所有的历史理算记录中筛选出本责任做的
     * 3. 根据条件区间，检查和本发票是同一个条件区间的
     * 4. 如果不根据费用类型区分，则最终获得一个数值，每个费用类型免赔以后往上面加
     * 5. 如果根据费用类型区分，则将map全部聚合
     */
    private BigDecimal historyDeductCalculator(InvoiceAdjustmentContext context, List<String> factorList) {
        if (context.getCurrentDeductAmount() != null) {
            log.info("直接获取历史免赔额：{}", context.getCurrentDeductAmount());
            return context.getCurrentDeductAmount();
        }

        if (context.getCurrentDeductAmountByFeeType() != null && context.getCurrentDeductAmountByFeeType().containsKey(context.getCurrentFeeType().getFeeType())) {
            log.info("直接获取费用类型{}的历史免赔额：{}", context.getCurrentFeeType().getFeeType(), context.getCurrentDeductAmountByFeeType().get(context.getCurrentFeeType().getFeeType()));
            return context.getCurrentDeductAmountByFeeType().get(context.getCurrentFeeType().getFeeType());
        }

        LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();

        // 1. 如果是按次，问题转化为本次免赔用了多少额度
        if (liabilityDeduct.getDeductPeriod().equals(DeductEnum.TIMES.getCode())) {
            context.setCurrentDeductAmount(BigDecimal.ZERO);
            // 1.1 如果是根据赔付金额区分的类型，那就一定不会有已经用了多少额度，因此这里这么取就可以了
            log.info("按次免赔金额，历史免赔金额为：{}", context.getCurrentDeductAmount());
            return context.getCurrentDeductAmount();
        }

        // 2. 如果是按年度，从所有的历史理算记录中筛选出本责任做的
        List<AdjustmentRecord> adjustmentRecordList = context.getHistoryAdjustmentRecord();
        List<AdjustmentRecord> inTimeRecordList = adjustmentRecordList.stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid()))
                    .filter(t -> DateUtil.isInPeriod(context.getPolicyStartDate(), context.getPolicyEndDate(), t.getVisitDate())).collect(Collectors.toList());


        List<AdjustmentRecord> inPeriodRecordList = inTimeRecordList;

        // 3. 根据条件区间，检查和本发票是同一个条件区间的
        for (String factor : factorList) {
            if (factor.equals(CartesianFactorEnum.SOCIAL_SECURITY.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHasYb().equals(context.getInvoice().getHasYb())).collect(Collectors.toList());
            }

            if (factor.equals(CartesianFactorEnum.HOSPITAL_LEVEL.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHospitalLevel().equals(context.getInvoice().getHospitalLevel())).collect(Collectors.toList());
            }

            if (factor.equals(CartesianFactorEnum.HOSPITAL_TYPE.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHospitalType().equals(context.getInvoice().getHospitalType())).collect(Collectors.toList());
            }
        }

        // 4. 如果不根据费用类型区分，则最终获得一个数值，每个费用类型免赔以后往上面加
        if (!factorList.contains(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
            context.setCurrentDeductAmount(inPeriodRecordList.stream().map(AdjustmentRecord::getDeductAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

            log.info("按年免赔金额，计算得到历史免赔额：{}", context.getCurrentDeductAmount());
            return context.getCurrentDeductAmount();
        }

        // 5. 如果根据费用类型区分，则将map全部聚合
        List<Map<String, BigDecimal>> historyDeductFeeMapList = inPeriodRecordList.stream().map(t -> AdjustUtil.stringToMap(t.getFeeDeductList())).collect(Collectors.toList());


        Map<String, BigDecimal> historyDeductFeeMap = AdjustUtil.aggregateMaps(historyDeductFeeMapList);

        context.setCurrentDeductAmountByFeeType(historyDeductFeeMap);
        //因为一次理算中不可能会一个费用结算多次，因此这里可以忽略
        BigDecimal currentFeeAmount = context.getCurrentDeductAmountByFeeType().get(context.getCurrentFeeType().getFeeType());
        if (currentFeeAmount == null) {
            currentFeeAmount = BigDecimal.ZERO;
        }
        log.info("计算得到费用类型{}的历史免赔额：{}", context.getCurrentFeeType().getFeeType(), currentFeeAmount);

        return currentFeeAmount;
    }

    /**
     * 计算这个免赔历史已经用了多少天
     * 必定是按年，且不可能根据费用类型区分
     *
     * 1. 从所有的历史理算记录中筛选出本责任做的
     * 2. 根据条件区间，检查和本发票是同一个条件区间的
     * 3. 最终获得一个数值
     */
    private Integer historyDeductDaysCalculator(InvoiceAdjustmentContext context, List<String> factorList) {
        LiabilityDeduct liabilityDeduct = context.getLiability().getLiabilityDeduct();

        // 0. 如果是按次，赔付天数直接返回0就行了
        if (liabilityDeduct.getDeductPeriod().equals(DeductEnum.TIMES.getCode())) {
            log.info("按次免赔天数，历史免赔天数为零");
            return 0;
        }

        // 1. 从所有的历史理算记录中筛选出本责任做的
        List<AdjustmentRecord> adjustmentRecordList = context.getHistoryAdjustmentRecord();

        List<AdjustmentRecord> inTimeRecordList = adjustmentRecordList.stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid()))
                .filter(t -> DateUtil.isInPeriod(context.getPolicyStartDate(), context.getPolicyEndDate(), t.getVisitDate())).collect(Collectors.toList());

        List<AdjustmentRecord> inPeriodRecordList = inTimeRecordList;

        // 2. 根据条件区间，检查和本发票是同一个条件区间的
        for (String factor : factorList) {
            if (factor.equals(CartesianFactorEnum.SOCIAL_SECURITY.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHasYb().equals(context.getInvoice().getHasYb())).collect(Collectors.toList());
            }

            if (factor.equals(CartesianFactorEnum.HOSPITAL_LEVEL.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHospitalLevel().equals(context.getInvoice().getHospitalLevel())).collect(Collectors.toList());
            }

            if (factor.equals(CartesianFactorEnum.HOSPITAL_TYPE.getCode())) {
                inPeriodRecordList = inTimeRecordList.stream().filter(t -> t.getInvoiceHospitalType().equals(context.getInvoice().getHospitalType())).collect(Collectors.toList());
            }
        }

        // 不可能根据费用类型区分

        // 3. 最终获得一个数值
        Integer historyDays = inPeriodRecordList.stream().map(AdjustmentRecord::getDeductDays).mapToInt(Integer::intValue).sum();
        log.info("按年免赔天数，计算得到历史免赔天数：{}", historyDays);

        return historyDays;
    }
}
