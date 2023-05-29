package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ProjectHeaderOa;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderOaService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * OA项目单头 ApplicationService
 *
 * @author 梅山源码
 */
public class ProjectHeaderOaApplicationService {

    @Resource
    private ProjectHeaderOaService projectHeaderOaService;

    /**
     * 创建OA项目单头
     *
     * @param projectHeaderOa OA项目单头
     * @return Id
     */
    public Long create(@Valid ProjectHeaderOa projectHeaderOa) {
        return projectHeaderOaService.create(projectHeaderOa).getId();
    }
}
