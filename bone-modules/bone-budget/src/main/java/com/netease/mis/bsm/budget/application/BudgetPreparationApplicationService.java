package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.BudgetPreparation;
import com.netease.mis.bsm.budget.domain.service.BudgetPreparationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算编制 ApplicationService
 *
 * @author 梅山源码
 */
public class BudgetPreparationApplicationService {

    @Resource
    private BudgetPreparationService preparationService;

    /**
     * 创建预算编制
     *
     * @param preparation 预算编制
     * @return Id
     */
    public Long create(@Valid BudgetPreparation preparation) {
        return preparationService.create(preparation).getId();
    }
}
