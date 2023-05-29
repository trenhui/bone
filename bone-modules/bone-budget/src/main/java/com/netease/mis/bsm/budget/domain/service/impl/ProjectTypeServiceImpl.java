package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectType;
import com.netease.mis.bsm.budget.domain.service.ProjectTypeService;

/**
 * 项目类型 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectTypeServiceImpl extends BaseServiceImpl<ProjectType, Long> implements ProjectTypeService {

    private final ProjectTypeRepository  projectTypeRepository;

    public ProjectTypeServiceImpl(ProjectTypeRepository  projectTypeRepository) {
        super(projectTypeRepository);
        this.projectTypeRepository = projectTypeRepository;
    }
}
