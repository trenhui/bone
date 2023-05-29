package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectHeader;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 项目单头 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectHeaderApplicationService {

    @Resource
    private ProjectHeaderService projectHeaderService;

    /**
     * 创建项目单头
     *
     * @param projectHeader 项目单头
     * @return Id
     */
    public Long create(@Valid ProjectHeader projectHeader) {
        return projectHeaderService.create(projectHeader).getId();
    }
}
