package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectEvent;
import com.netease.mis.bsm.budget.domain.service.ProjectEventService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 事件 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectEventApplicationService {

    @Resource
    private ProjectEventService projectEventService;

    /**
     * 创建事件
     *
     * @param projectEvent 事件
     * @return Id
     */
    public Long create(@Valid ProjectEvent projectEvent) {
        return projectEventService.create(projectEvent).getId();
    }
}
