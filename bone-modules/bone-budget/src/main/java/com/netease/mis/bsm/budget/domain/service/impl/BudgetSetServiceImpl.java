package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.BudgetSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.BudgetSet;
import com.netease.mis.bsm.budget.domain.service.BudgetSetService;

/**
 * 预算设置 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class BudgetSetServiceImpl extends BaseServiceImpl<BudgetSet, Long> implements BudgetSetService {

    private final BudgetSetRepository  setRepository;

    public BudgetSetServiceImpl(BudgetSetRepository  setRepository) {
        super(setRepository);
        this.setRepository = setRepository;
    }
}
