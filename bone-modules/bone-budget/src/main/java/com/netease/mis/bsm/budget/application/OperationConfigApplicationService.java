package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.OperationConfig;
import com.netease.mis.bsm.budget.domain.service.OperationConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算操作配置 ApplicationService
 *
 * @author 梅山源码
 */
public class OperationConfigApplicationService {

    @Resource
    private OperationConfigService operationConfigService;

    /**
     * 创建预算操作配置
     *
     * @param operationConfig 预算操作配置
     * @return Id
     */
    public Long create(@Valid OperationConfig operationConfig) {
        return operationConfigService.create(operationConfig).getId();
    }
}
