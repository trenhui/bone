package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.BudgetSet;
import com.netease.mis.bsm.budget.domain.service.BudgetSetService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算设置 ApplicationService
 *
 * @author 梅山源码
 */
public class BudgetSetApplicationService {

    @Resource
    private BudgetSetService setService;

    /**
     * 创建预算设置
     *
     * @param set 预算设置
     * @return Id
     */
    public Long create(@Valid BudgetSet set) {
        return setService.create(set).getId();
    }
}
