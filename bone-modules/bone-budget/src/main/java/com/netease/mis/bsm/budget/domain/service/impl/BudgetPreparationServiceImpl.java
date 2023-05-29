package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.BudgetPreparationRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.BudgetPreparation;
import com.netease.mis.bsm.budget.domain.service.BudgetPreparationService;

/**
 * 预算编制 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class BudgetPreparationServiceImpl extends BaseServiceImpl<BudgetPreparation, Long> implements BudgetPreparationService {

    private final BudgetPreparationRepository  preparationRepository;

    public BudgetPreparationServiceImpl(BudgetPreparationRepository  preparationRepository) {
        super(preparationRepository);
        this.preparationRepository = preparationRepository;
    }
}
