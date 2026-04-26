package com.bone.tpa.sdk.adjustment.model.liability;

import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class InvoiceAdjustmentContext {
    private Policy policy;
    private Date policyStartDate;
    private Date policyEndDate;

    private Claim claim;
    private ClaimInvoice invoice;
    private ClaimStakeholder outInsure;
    private Plan plan;
    private Coverage coverage;
    private LiabilityConfig liability;
    private String recordName; // 展示在理算记录中的责任名称。如果该责任是后付责任格式会是 formerName -> liability.name
    private List<LiabilityConfig> allLiability;
    private List<LiabilitySharing> sharingList;
    private List<LiabilitySharingRelation> sharingRelationList;

    private PersonalQuota personalQuota; //这个出险人的个人额度

    private Map<String, BigDecimal> quotaMap; //全部控额的map
    private List<String> feeTypeList; //承担的金额类型
    private Map<String, BigDecimal> alarmQuotaMap; // 非强制控额的map

    private BigDecimal liabilityLimit; //责任维度的限额
    private BigDecimal liabilityPayAmount; //该责任该发票的赔付额
    private BigDecimal remainingLiabilityDeductLimit; //到本次为止的剩余免赔额
    private BigDecimal liabilityDeductAmount; //该责任该发票的免赔额
    private Integer liabilityDeductDays; //该责任该发票的免赔天数
    private BigDecimal currentDeductAmount; //当前这次免赔已经用了的额度

    private Map<String, BigDecimal> invoiceFeeHistoryPayoutMap; //不同费用赔付的历史赔付额

    private Map<String, BigDecimal> liabilityFeeMap; //责任金额成分及对应的金额

    //后付责任使用的字段
    private Map<String, BigDecimal> liabilityFeeOccupyDifferenceMap; //责任金额成分及对应的 占据额-赔付额 的金额
                                                                     //用于在进行后付责任计算的时候额外占据金额不让赔付
    private Boolean nextLiabilityFlag = false;

    private String formerName; // 这个责任如果是后付责任，这个责任的前一个责任的名称

    //实际理算时使用的字段
    private InvoiceFeeType currentFeeType; //当前的费用类型。医疗保险型限定

    private BigDecimal currentInvoiceFeeAmount; //当前费用类型对应的金额。医疗保险型限定

    private BigDecimal deductRatio; //免赔比例。公式专用

    private List<AdjustmentRecord> historyAdjustmentRecord; //历史理算记录

    private Map<String, BigDecimal> currentDeductAmountByFeeType; //当前这次免赔已经用了的额度

    //直付额度
    //key都是公账的保单号
    private Map<String, BigDecimal> publicAccountMap; //公账额度
    private Map<String, BigDecimal> publicAccountPayMap; //公账赔付额
    private BigDecimal personalAccount; //个账额度
    private BigDecimal personalAccountPay; //个账赔付额

    private BigDecimal directAmount; //本责任的公账额度


    public InvoiceAdjustmentContext twoDecimalsFormatter() {
        if (this.liabilityLimit != null) {
            this.liabilityLimit = this.liabilityLimit.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.liabilityPayAmount != null) {
            this.liabilityPayAmount = this.liabilityPayAmount.setScale(2, RoundingMode.HALF_UP);
        }

        return this; // 支持链式调用
    }
}
