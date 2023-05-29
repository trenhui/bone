package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.BudgetStrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.BudgetStrategy;
import com.netease.mis.bsm.budget.domain.service.BudgetStrategyService;

/**
 * 预算策略 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class BudgetStrategyServiceImpl extends BaseServiceImpl<BudgetStrategy, Long> implements BudgetStrategyService {

    private final BudgetStrategyRepository  strategyRepository;

    public BudgetStrategyServiceImpl(BudgetStrategyRepository  strategyRepository) {
        super(strategyRepository);
        this.strategyRepository = strategyRepository;
    }
}
