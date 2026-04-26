package com.bone.tpa.intelligent.adjustment.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 公式参数
 */
@Data
public class FormulaFactor {

    private String feeType;

    private BigDecimal money;

    private BigDecimal ratio;

    private BigDecimal deduct = BigDecimal.ZERO;

    private BigDecimal limit = BigDecimal.ZERO;




    private Long allowanceDays;

    private Integer deductDays;

    /**
     * 该类专用于生成带数字的，单个费用类型维度的理算公式，一共有以下几种情况：
     *
     * 1. 医疗报销
     * 1.1. 无免赔 & 免赔额为0 & 免赔金额-控额为0
     *      min(money * ratio, limit)
     *
     * 1.2. 免赔金额-绝对免赔 & 免赔金额-相对免赔且数字少于
     * 1.2.1。 免赔对象为发票金额
     *      min((money - deduct) * ratio, limit)
     * 1.2.2。 免赔对象为理算金额
     *      min(money * ratio - deduct, limit)
     *
     * 1.3. 免赔比例
     *      min(money * ratio * (1 - deduct), limit)
     *
     *
     * 2.
     *
     *
     *
     *
     *
     *
     * 3. 津贴给付型
     * 3.1. 无免赔 & 免赔额为0 & 免赔金额-控额为0 & 免赔天数-天数为0
     *      min(money * allowanceDays * ratio, limit)
     *
     * 3.2. 免赔金额-绝对免赔 & 免赔金额-相对免赔且数字少于
     * 3.2.1。 免赔对象为理算金额。该类型理算不可能为发票金额进行免赔
     *      min(money * allowanceDays * ratio - deduct, limit)
     *
     * 3.3. 免赔比例
     *      min(money * allowanceDays * ratio * (1 - deduct), limit)
     *
     * 3.4. 免赔天数
     *      min(money * (allowanceDays - deduct) * ratio, limit)
     */

    public FormulaFactor twoDecimalsFormatter() {
        if (this.money != null) {
            this.money = this.money.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.ratio != null) {
            this.ratio = this.ratio.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.deduct != null) {
            this.deduct = this.deduct.setScale(2, RoundingMode.HALF_UP);
        }
        if (this.limit != null) {
            this.limit = this.limit.setScale(2, RoundingMode.HALF_UP);
        }
        return this; // 支持链式调用
    }
}
