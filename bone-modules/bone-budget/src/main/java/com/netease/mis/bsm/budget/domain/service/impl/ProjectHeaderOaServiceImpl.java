package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectHeaderOaRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectHeaderOa;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderOaService;

/**
 * OA项目单头 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectHeaderOaServiceImpl extends BaseServiceImpl<ProjectHeaderOa, Long> implements ProjectHeaderOaService {

    private final ProjectHeaderOaRepository  projectHeaderOaRepository;

    public ProjectHeaderOaServiceImpl(ProjectHeaderOaRepository  projectHeaderOaRepository) {
        super(projectHeaderOaRepository);
        this.projectHeaderOaRepository = projectHeaderOaRepository;
    }
}
