package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.liability.InvoiceAdjustmentContext;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;

public interface LiabilityAdjustmentStrategy {

    /**
     * 检查是否符合责任特殊要求
     */
    void checkAdjustable(InvoiceAdjustmentContext context);

    /**
     * 处理不同责任类型的理算逻辑。
     *
     * @param context 理算上下文，包含赔案、发票和责任配置信息。
     * @return 理算结果，包含赔付金额和相关细节。
     */
    AdjustmentRecord adjustmentInvoice(InvoiceAdjustmentContext context);

    /**
     * 生成该责任的理算公式
     *
     * @param liability 责任配置信息
     * @return 理算公式，仅有展示作用。
     */
    String buildAdjustFormula(LiabilityConfig liability);

    /**
     * 获取该策略支持的责任类型
     *
     * @return 责任类型
     */
    LiabilityTypeEnum getSupportedType();
}
