package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.BudgetRule;
import com.netease.mis.bsm.budget.domain.service.BudgetRuleService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算规则 ApplicationService
 *
 * @author 梅山源码
 */
public class BudgetRuleApplicationService {

    @Resource
    private BudgetRuleService ruleService;

    /**
     * 创建预算规则
     *
     * @param rule 预算规则
     * @return Id
     */
    public Long create(@Valid BudgetRule rule) {
        return ruleService.create(rule).getId();
    }
}
