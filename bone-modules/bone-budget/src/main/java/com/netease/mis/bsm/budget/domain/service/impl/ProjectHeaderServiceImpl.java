package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectHeaderRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectHeader;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderService;

/**
 * 项目单头 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectHeaderServiceImpl extends BaseServiceImpl<ProjectHeader, Long> implements ProjectHeaderService {

    private final ProjectHeaderRepository  projectHeaderRepository;

    public ProjectHeaderServiceImpl(ProjectHeaderRepository  projectHeaderRepository) {
        super(projectHeaderRepository);
        this.projectHeaderRepository = projectHeaderRepository;
    }
}
