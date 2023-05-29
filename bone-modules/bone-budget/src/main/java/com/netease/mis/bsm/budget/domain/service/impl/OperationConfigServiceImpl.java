package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.OperationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.OperationConfig;
import com.netease.mis.bsm.budget.domain.service.OperationConfigService;

/**
 * 预算操作配置 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class OperationConfigServiceImpl extends BaseServiceImpl<OperationConfig, Long> implements OperationConfigService {

    private final OperationConfigRepository  operationConfigRepository;

    public OperationConfigServiceImpl(OperationConfigRepository  operationConfigRepository) {
        super(operationConfigRepository);
        this.operationConfigRepository = operationConfigRepository;
    }
}
