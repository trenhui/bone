package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectType;
import com.netease.mis.bsm.budget.domain.service.ProjectTypeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 项目类型 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectTypeApplicationService {

    @Resource
    private ProjectTypeService projectTypeService;

    /**
     * 创建项目类型
     *
     * @param projectType 项目类型
     * @return Id
     */
    public Long create(@Valid ProjectType projectType) {
        return projectTypeService.create(projectType).getId();
    }
}
