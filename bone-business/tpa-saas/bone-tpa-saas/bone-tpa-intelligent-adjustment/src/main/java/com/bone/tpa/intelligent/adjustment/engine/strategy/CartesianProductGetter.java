package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.tpa.intelligent.adjustment.engine.constant.AdjustmentConstant;
import com.bone.tpa.intelligent.adjustment.enums.CartesianFactorEnum;
import com.bone.tpa.intelligent.adjustment.enums.RangeTypeEnum;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceAdjustmentContext;
import com.bone.tpa.sdk.adjustment.model.liability.RangeObject;
import com.bone.tpa.intelligent.adjustment.service.AdjustmentRecordService;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.service.ClaimInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * 免赔额计算器，负责计算发票的免赔额。
 * 根据不同的免赔额类型（绝对免赔额、相对免赔额等），计算相应的免赔金额。
 */
@Component
@Slf4j
public class CartesianProductGetter {

    @Autowired
    private InvoiceFeeCalculator invoiceFeeCalculator;

    @Autowired
    private AdjustmentRecordService adjustmentRecordService;

    @Autowired
    private ClaimInfoService claimInfoService;

    private final String DEFAULT_KEY = "DEFAULT";
    private final String FORMAT = "%s=%s";


    /**
     * 笛卡尔积构建和取值
     */
    public BigDecimal valueGetter(InvoiceAdjustmentContext context, List<String> factorList, Map<String, List<RangeObject>> rangeMap, Map<String, BigDecimal> valueMap) {

        if (factorList == null || factorList.isEmpty()) {
            return valueMap.get(DEFAULT_KEY);
        }

        //特殊判断。如果只有一个值而且MEDICAL_INSURANCE就直接返回
        if (factorList.size() == 1 && factorList.get(0).equals(CartesianFactorEnum.MEDICAL_INSURANCE.getCode())) {
            return valueMap.get(DEFAULT_KEY);
        }

        List<String> keyBuilder = new ArrayList<>();

        for (String factor : factorList) {
            if (factor.equals(CartesianFactorEnum.NONE.getCode())) {
                return valueMap.get(DEFAULT_KEY);
            }

//            if (factor.equals(CartesianFactorEnum.PERSONAL_AMOUNT.getCode())) {
//                //如果是个人额度就直接跳出来了
//                return valueMap.get(CartesianFactorEnum.PERSONAL_AMOUNT.getCode());
//            }

            if (factor.equals(CartesianFactorEnum.SOCIAL_SECURITY.getCode())) {
                //如果是区分医保赔付，要去除发票上的是否有医保
                //空白默认为无医保
                if (Objects.equals(context.getInvoice().getHasYb(), AdjustmentConstant.TRUE)) {
                    keyBuilder.add(String.format(FORMAT, factor, "有医保"));
                } else {
                    keyBuilder.add(String.format(FORMAT, factor, "无医保"));
                }
            }
            if (factor.equals(CartesianFactorEnum.HOSPITAL_LEVEL.getCode())) {
                if (context.getInvoice().getHospitalLevel() == null) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票的医院等级为空");
                }
                //如果是医院等级，要去取出发票上的医院等级字段
                keyBuilder.add(String.format(FORMAT, factor, context.getInvoice().getHospitalLevel()));
            }
            if (factor.equals(CartesianFactorEnum.HOSPITAL_TYPE.getCode())) {
                if (context.getInvoice().getHospitalType() == null) {
                    throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "发票的医院类型为空");
                }
                //如果是医院性质，要去取出发票上的医院性质字段
                keyBuilder.add(String.format(FORMAT, factor, context.getInvoice().getHospitalType()));
            }
            if (factor.equals(CartesianFactorEnum.LIABILITY_FEE.getCode())) {
                //如果是责任承担费用，则要把承担费用拿出来
                keyBuilder.add(String.format(FORMAT, factor, context.getCurrentFeeType().getFeeType()));
            }

            //取出相应的区间设置
            List<RangeObject> correspondList = rangeMap.get(factor);
            if (correspondList == null) {
                continue;
            }

            if (factor.equals(CartesianFactorEnum.SEVERENESS.getCode())) {
                //如果是区分严重等级，这里要寻找赔案上的严重程度
                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, context.getClaim().getSeverenessLevel())));
            }
            if (factor.equals(CartesianFactorEnum.AGE.getCode())) {
                //如果是区分年龄，则对比出险人的年龄区间
                Integer age = AdjustUtil.calculateAge(context.getOutInsure().getIdentityNo());
                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, age)));
            }
            if (factor.equals(CartesianFactorEnum.TOTAL_FEE.getCode())) {
                //如果是发票总费用区间，要根据发票上的总费用寻找区间设置
                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, context.getInvoice().getTotalAmount())));
            }
            if (factor.equals(CartesianFactorEnum.ADJUSTMENT_FEE.getCode())) {
                //如果是案件理算费用区间，则是根据对应的承担费用类型的金额决定要多少
                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, context.getCurrentInvoiceFeeAmount())));
            }
            if (factor.equals(CartesianFactorEnum.CLAIM_ACCUM.getCode())) {
                //如果是赔案累计区间，要去查询发票表
                List<AdjustmentRecord> recordList = context.getHistoryAdjustmentRecord().stream().filter(t -> t.getLiabilityUuid().equals(context.getLiability().getUuid())).collect(Collectors.toList());
                List<ClaimInvoice> invoiceList = new ArrayList<>();
                if (!recordList.isEmpty()) {
                    List<Long> invoiceIdList = recordList.stream().map(AdjustmentRecord::getInvoiceId).collect(Collectors.toList());
                    log.info("赔案累计区间，历史发票id: {}", invoiceIdList);
                    invoiceList.addAll(claimInfoService.getInvoiceListByIdList(invoiceIdList));
                }

                //这个累计是要算上当前发票的
                invoiceList.add(context.getInvoice());

                //这里暂时不把查到的东西存起来了，调用次数应该是较少的
                //对对应费用类型求和
                BigDecimal totalAccum = invoiceList.stream().map(t -> invoiceFeeCalculator.calculateInvoiceFee(context, context.getCurrentFeeType(), t)).reduce(BigDecimal.ZERO, BigDecimal::add);

                //然后构造
                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, totalAccum)));
            }
            if (factor.equals(CartesianFactorEnum.PAY_TIMES.getCode())) {
                //如果是区分赔付次数区间，则要查询全部的记录
                List<AdjustmentRecord> recordList = adjustmentRecordService.getAdjustmentRecordByLiability(context.getLiability().getUuid(), null, false);

                keyBuilder.add(String.format(FORMAT, factor, findCorrespondRange(correspondList, recordList.size())));
            }
        }

        String finalKey = String.join(";", keyBuilder);

        log.info("组合key：{}", finalKey);

        if (valueMap.get(finalKey) != null) {
            return valueMap.get(finalKey);
        }

        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "未获取到对应笛卡尔积值: " + finalKey);
    }

    /**
     * 在区间内匹配，然后返回拼凑完成的区间字符串
     *
     * @param rangeList 该类型的区间
     * @param value     需要匹配的值
     * @return
     */
    private String findCorrespondRange(List<RangeObject> rangeList, Object value) {
        for (RangeObject range : rangeList) {
            RangeTypeEnum rangeType = RangeTypeEnum.getByCode(range.getIntervalType());
            if (rangeType != null) {
                String result = rangeType.makeRangeString(value, range.getLowerLimit(), range.getUpperLimit());

                if (result != null) {
                    return result;
                }
            }
        }
        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "未找到匹配区间: " + value);
    }

}
