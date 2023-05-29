package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectTransfer;
import com.netease.mis.bsm.budget.domain.service.ProjectTransferService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算MPC转移项目 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectTransferApplicationService {

    @Resource
    private ProjectTransferService projectTransferService;

    /**
     * 创建预算MPC转移项目
     *
     * @param projectTransfer 预算MPC转移项目
     * @return Id
     */
    public Long create(@Valid ProjectTransfer projectTransfer) {
        return projectTransferService.create(projectTransfer).getId();
    }
}
