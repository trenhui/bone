package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.BudgetStrategy;
import com.netease.mis.bsm.budget.domain.service.BudgetStrategyService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算策略 ApplicationService
 *
 * @author 梅山源码
 */
public class BudgetStrategyApplicationService {

    @Resource
    private BudgetStrategyService strategyService;

    /**
     * 创建预算策略
     *
     * @param strategy 预算策略
     * @return Id
     */
    public Long create(@Valid BudgetStrategy strategy) {
        return strategyService.create(strategy).getId();
    }
}
