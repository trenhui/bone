package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectEvent;
import com.netease.mis.bsm.budget.domain.service.ProjectEventService;

/**
 * 事件 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectEventServiceImpl extends BaseServiceImpl<ProjectEvent, Long> implements ProjectEventService {

    private final ProjectEventRepository  projectEventRepository;

    public ProjectEventServiceImpl(ProjectEventRepository  projectEventRepository) {
        super(projectEventRepository);
        this.projectEventRepository = projectEventRepository;
    }
}
