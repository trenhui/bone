package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectEmployee;
import com.netease.mis.bsm.budget.domain.service.ProjectEmployeeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 项目成员 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectEmployeeApplicationService {

    @Resource
    private ProjectEmployeeService projectEmployeeService;

    /**
     * 创建项目成员
     *
     * @param projectEmployee 项目成员
     * @return Id
     */
    public Long create(@Valid ProjectEmployee projectEmployee) {
        return projectEmployeeService.create(projectEmployee).getId();
    }
}
