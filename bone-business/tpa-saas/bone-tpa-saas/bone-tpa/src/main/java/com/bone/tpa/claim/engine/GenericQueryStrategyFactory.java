package com.bone.tpa.claim.engine;

import com.bone.tpa.claim.application.enums.BizModelEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GenericQueryStrategyFactory {

    private final Map<BizModelEnum, GenericQueryStrategy> strategyMap;

    /**
     * 构造方法，注入所有的策略实现并根据责任类型将其映射到对应的策略。
     *
     * @param strategies 注入的所有策略实现
     */
    @Autowired
    public GenericQueryStrategyFactory(List<GenericQueryStrategy> strategies) {
        // 根据责任类型（LiabilityType）将每个策略与其类型映射
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        GenericQueryStrategy::getBizModel,  // 获取每个策略支持的责任类型
                        Function.identity()  // 将策略本身作为值
                ));
    }


    /**
     * 根据责任配置返回对应的理算策略。
     *
     * @return 对应的理算策略
     */
    public GenericQueryStrategy getBizModelStrategy(BizModelEnum bizModelEnum) {
        GenericQueryStrategy strategy = strategyMap.get(bizModelEnum);

        if (strategy == null) {
            return strategyMap.get(BizModelEnum.DEFAULT);
        }


        return strategy;
    }

    /**
     * 根据责任配置返回对应的理算策略。
     *
     * @return 对应的理算策略
     */
    public GenericQueryStrategy getBizModelStrategy(String bizModel) {
        BizModelEnum bizModelEnum = BizModelEnum.getByCode(bizModel);
        return getBizModelStrategy(bizModelEnum);
    }
}
