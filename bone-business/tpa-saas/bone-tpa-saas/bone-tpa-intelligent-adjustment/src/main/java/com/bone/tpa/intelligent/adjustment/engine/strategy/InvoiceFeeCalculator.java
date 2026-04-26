package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.metadata.sdk.MetadataFetchEngine;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.metadata.sdk.enums.FieldSource;
import com.bone.tpa.intelligent.adjustment.enums.InvoiceFeeTypeSourceEnum;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceAdjustmentContext;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceFeeType;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发票金额计算器，负责计算发票对应发票金额的数值。
 */
@Component
@Slf4j
public class InvoiceFeeCalculator {

    private static final String appCode = "tpa";
    private static final String tableName = "ss_claim_invoice";

    @Autowired
    private MetadataFetchEngine metadataFetchEngine;

    private final Logger logger = LoggerFactory.getLogger(InvoiceFeeCalculator.class);

    /**
     * 计算要承担的发票金额
     *
     *
     * @param context
     * @param invoiceFeeType
     * @return
     */
    public BigDecimal calculateInvoiceFee(InvoiceAdjustmentContext context, InvoiceFeeType invoiceFeeType) {
        //如果是录入项，直接去找同样名字的
        if (invoiceFeeType.getSource().equals(InvoiceFeeTypeSourceEnum.INPUT.getCode())) {
            if (invoiceFeeType.getFeeType().equals("总自费")) {
                return context.getInvoice().getTotalSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("丙类自费")) {
                return context.getInvoice().getClassCSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("超限价自付")) {
                return context.getInvoice().getExcessLimitSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("自付二")) {
                return context.getInvoice().getSelfPayPart2Amount();
            }
            if (invoiceFeeType.getFeeType().equals("合理金额")) {
                return context.getInvoice().getValidAmount();
            }

            //前面是固定值，后面的就是非默认的添加的录入项了
            Object invoiceFeeAmount = context.getInvoice().getExtraProperties().get(invoiceFeeType.getFeeType());
            if (invoiceFeeAmount instanceof BigDecimal) { //todo 这里要注意一下
                return (BigDecimal) invoiceFeeAmount;
            } else {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "承担金额错误: " + invoiceFeeType.getFeeType());
            }
        } else {
            try {
                //获取全部可能在公式里出现的金额成分，并且筛选出非默认的
                List<InvoiceFeeType> parameters = context.getLiability().getRestrictOutInsure().getInvoiceFeeType().stream().filter(InvoiceFeeType::getOpen).toList();
                parameters = parameters.stream().filter(t -> !t.getIsDefault()).toList();

                BigDecimal calculateAmount = (BigDecimal) formulaCalculate(context.getInvoice(), parameters, invoiceFeeType.getCalculateFormula());

                Object invoiceFeeAmount = context.getInvoice().getExtraProperties().get(invoiceFeeType.getFeeType());

                if (invoiceFeeAmount instanceof BigDecimal) {
                    if (calculateAmount.equals(invoiceFeeAmount)) {
                        return calculateAmount;
                    } else {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "计算额和录入额不同: " + invoiceFeeAmount + "--" + calculateAmount);
                    }
                }

                return calculateAmount;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }



    public BigDecimal calculateInvoiceFee(InvoiceAdjustmentContext context, InvoiceFeeType invoiceFeeType, ClaimInvoice claimInvoice) {
        //如果是录入项，直接去找同样名字的
        if (invoiceFeeType.getSource().equals(InvoiceFeeTypeSourceEnum.INPUT.getCode())) {
            if (invoiceFeeType.getFeeType().equals("总自费")) {
                return claimInvoice.getTotalSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("丙类自费")) {
                return claimInvoice.getClassCSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("超限价自付")) {
                return claimInvoice.getExcessLimitSelfPayAmount();
            }
            if (invoiceFeeType.getFeeType().equals("自付二")) {
                return claimInvoice.getSelfPayPart2Amount();
            }
            if (invoiceFeeType.getFeeType().equals("合理金额")) {
                return claimInvoice.getValidAmount();
            }

            //前面是固定值，后面的就是非默认的添加的录入项了
            Object invoiceFeeAmount = claimInvoice.getExtraProperties().get(invoiceFeeType.getFeeType());
            if (invoiceFeeAmount instanceof BigDecimal) { //todo 这里要注意一下
                return (BigDecimal) invoiceFeeAmount;
            } else {
                throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "承担金额错误: " + invoiceFeeType.getFeeType());
            }
        } else {
            try {
                //获取全部可能在公式里出现的金额成分，并且筛选出非默认的
                List<InvoiceFeeType> parameters = context.getLiability().getRestrictOutInsure().getInvoiceFeeType().stream().filter(InvoiceFeeType::getOpen).toList();
                parameters = parameters.stream().filter(t -> !t.getIsDefault()).toList();

                BigDecimal calculateAmount = (BigDecimal) formulaCalculate(claimInvoice, parameters, invoiceFeeType.getCalculateFormula());

                Object invoiceFeeAmount = claimInvoice.getExtraProperties().get(invoiceFeeType.getFeeType());

                if (invoiceFeeAmount instanceof BigDecimal) {
                    if (calculateAmount.equals(invoiceFeeAmount)) {
                        return calculateAmount;
                    } else {
                        throw new TpaBizException(BizErrorCode.ADJUST_NOT_ALLOWED, "计算额和录入额不同: " + invoiceFeeAmount + "--" + calculateAmount);
                    }
                }

                return calculateAmount;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * 承担金额，公式计算方法
     * @param invoice
     * @param formula   公式
     * @return
     * */
    public Object formulaCalculate(ClaimInvoice invoice, List<InvoiceFeeType> parameters, String formula) throws Exception {
        ExpressRunner runner = new ExpressRunner(true, false);
        DefaultContext<String, Object> formulaContext = new DefaultContext<String, Object>();

        //从发票中获取全部的默认金额字段
        formulaContext.put("发票总费用", invoice.getTotalAmount());
        formulaContext.put("基金总支付", invoice.getTotalMedicalFundPayment());
        formulaContext.put("基本统筹金额", invoice.getBasicPoolingAmount());
        formulaContext.put("统筹起付线", invoice.getPoolingThreshold());
        formulaContext.put("统筹赔付比例", invoice.getPoolingReimbursementRate());
        formulaContext.put("自付一", invoice.getSelfPayPart1Amount());
        formulaContext.put("自付二", invoice.getSelfPayPart2Amount());
        formulaContext.put("总自费", invoice.getTotalSelfPayAmount());
        formulaContext.put("丙类自费", invoice.getClassCSelfPayAmount());
        formulaContext.put("超限价自付", invoice.getExcessLimitSelfPayAmount());
        formulaContext.put("三方已赔", invoice.getThirdPartyPaidAmount());
        formulaContext.put("不合理金额", invoice.getInvalidAmount());

        //将非默认的字段筛选成列表
        List<InvoiceFeeType> customFeeType = parameters.stream().filter(t -> !t.getIsDefault()).toList();
        //如果这个列表为空，就不用多调用接口了，不然就要调用全部字段的接口
        if (!customFeeType.isEmpty()) {

            //获取扩展字段
            List<MetaFieldDTO> allFieldList = metadataFetchEngine.getAllBizIdentityField(appCode, tableName, invoice.getBizIdentityCode());
            List<MetaFieldDTO> extendFieldList = allFieldList.stream().filter(t -> t.getSource() == FieldSource.BIZ_EXTEND).toList();

            // 字段中文名，MetaFieldDTO
            Map<String, MetaFieldDTO> extendFieldMap = extendFieldList.stream().collect(Collectors.toMap(MetaFieldDTO::getFieldRemark, t -> t));

            for (InvoiceFeeType feeType : customFeeType) {
                MetaFieldDTO fieldDTO = extendFieldMap.get(feeType.getFeeType());

                if (fieldDTO == null) {
                    formulaContext.put(feeType.getFeeType(), 0);
                } else {
                    Object value = invoice.getExtraProperties().get(fieldDTO.getFieldName());
                    if (value != null) {
                        formulaContext.put(feeType.getFeeType(), value);
                    } else {
                        formulaContext.put(feeType.getFeeType(), 0);
                    }
                }
            }
        }

        logger.error("开始计算承担金额, 公式: {}, 参数包括: {}", formula, formulaContext);

        Object r = runner.execute(formula, formulaContext, null, true, false);

        logger.error("计算结束：{}", r);

        return r;
    }

}
