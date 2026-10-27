package com.bone.synap.settlement.engine;

import com.bone.synap.settlement.Money;

import java.util.HashMap;
import java.util.Map;

/**
 * 多维度控额（min 聚合）。
 * 对应《理算引擎内部设计》§4 LimitControl 与 tpa-saas AdjustUtil.findMin
 * （个人/责任/计划/险种/共保/就诊/次日/三方直付多维度取交）。
 *
 * 维度键约定：
 *   "L:"+liabilityId   责任剩余控额
 *   "F:"+feeType       费用类型子限额剩余
 *   "P:"+planId        计划维度剩余
 * 消费某维度时同步扣减，确保「占额」不会双计（ACORD 三值额度模型：initial−claim−frozen）。
 */
public final class LimitControl {
    private final Map<String, Money> rem = new HashMap<>();

    public void put(String key, Money value) {
        rem.put(key, value.clampNonNegative());
    }

    public Money remaining(String key) {
        return rem.getOrDefault(key, Money.ZERO);
    }

    public boolean contains(String key) {
        return rem.containsKey(key);
    }

    public void consume(String key, Money amount) {
        Money r = rem.get(key);
        if (r != null) rem.put(key, r.subtract(amount).clampNonNegative());
    }
}
