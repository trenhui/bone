package com.bone.tpa.intelligent.adjustment.engine.strategy;

import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LiabilityAdjustmentStrategyFactory {

    private final Map<LiabilityTypeEnum, LiabilityAdjustmentStrategy> strategyMap;

    /**
     * 构造方法，注入所有的策略实现并根据责任类型将其映射到对应的策略。
     *
     * @param strategies 注入的所有策略实现
     */
    @Autowired
    public LiabilityAdjustmentStrategyFactory(List<LiabilityAdjustmentStrategy> strategies) {
        // 根据责任类型（LiabilityType）将每个策略与其类型映射
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        LiabilityAdjustmentStrategy::getSupportedType,  // 获取每个策略支持的责任类型
                        Function.identity()  // 将策略本身作为值
                ));
    }


    /**
     * 根据责任配置返回对应的理算策略。
     *
     * @param liabilityType 配置的责任类型
     * @return 对应的理算策略
     */
    public LiabilityAdjustmentStrategy getLiabilityAdjudicationStrategy(LiabilityTypeEnum liabilityType) {
        LiabilityAdjustmentStrategy strategy = strategyMap.get(liabilityType);

        if (strategy == null) {
            throw new IllegalArgumentException("未找到适用于此责任类型的理算策略: " + liabilityType);
        }

        return strategy;
    }
}
