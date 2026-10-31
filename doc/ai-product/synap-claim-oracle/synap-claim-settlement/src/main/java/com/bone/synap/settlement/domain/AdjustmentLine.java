package com.bone.synap.settlement.domain;

import com.bone.synap.settlement.Money;

/**
 * 单费用成分理算结果（六成分）。
 * 对应《理算引擎内部设计》§3.2 AdjustmentLine 与 tpa-saas feeCalculate/ratioPayout/
 * feePayout/feeDeduct/feeOccupy/feeRemain 六张 map 的强类型化。
 */
public final class AdjustmentLine {
    public final String feeType;       // 费用类型编码（目录编码，非名称字符串）
    public final Money feeCalculate;   // 控额前赔付额（四舍五入后）
    public final Money ratioPayout;    // 命中赔付比例（百分比）
    public final Money feePayout;      // 最终赔付额（经多维度 min 控额；拒赔时为 0）
    public final Money feeDeduct;      // 免赔额合计
    public final Money feeOccupy;      // 占用额（= feePayout / 比例，封顶 current）
    public final Money feeRemain;      // 理算前剩余（current - feePayout 近似）
    /** 拒赔类型（目录外/免责项目）；null 表示正常赔付。 */
    public final String rejectType;

    public AdjustmentLine(String feeType, Money feeCalculate, Money ratioPayout,
                          Money feePayout, Money feeDeduct, Money feeOccupy, Money feeRemain) {
        this(feeType, feeCalculate, ratioPayout, feePayout, feeDeduct, feeOccupy, feeRemain, null);
    }

    public AdjustmentLine(String feeType, Money feeCalculate, Money ratioPayout,
                          Money feePayout, Money feeDeduct, Money feeOccupy, Money feeRemain, String rejectType) {
        this.feeType = feeType;
        this.feeCalculate = feeCalculate;
        this.ratioPayout = ratioPayout;
        this.feePayout = feePayout;
        this.feeDeduct = feeDeduct;
        this.feeOccupy = feeOccupy;
        this.feeRemain = feeRemain;
        this.rejectType = rejectType;
    }
}
