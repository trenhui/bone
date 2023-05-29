package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.BudgetRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.BudgetRule;
import com.netease.mis.bsm.budget.domain.service.BudgetRuleService;

/**
 * 预算规则 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class BudgetRuleServiceImpl extends BaseServiceImpl<BudgetRule, Long> implements BudgetRuleService {

    private final BudgetRuleRepository  ruleRepository;

    public BudgetRuleServiceImpl(BudgetRuleRepository  ruleRepository) {
        super(ruleRepository);
        this.ruleRepository = ruleRepository;
    }
}
