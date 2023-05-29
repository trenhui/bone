package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectEmployee;
import com.netease.mis.bsm.budget.domain.service.ProjectEmployeeService;

/**
 * 项目成员 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectEmployeeServiceImpl extends BaseServiceImpl<ProjectEmployee, Long> implements ProjectEmployeeService {

    private final ProjectEmployeeRepository  projectEmployeeRepository;

    public ProjectEmployeeServiceImpl(ProjectEmployeeRepository  projectEmployeeRepository) {
        super(projectEmployeeRepository);
        this.projectEmployeeRepository = projectEmployeeRepository;
    }
}
