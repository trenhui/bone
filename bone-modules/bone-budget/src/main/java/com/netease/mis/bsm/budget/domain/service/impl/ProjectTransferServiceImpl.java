package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ProjectTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ProjectTransfer;
import com.netease.mis.bsm.budget.domain.service.ProjectTransferService;

/**
 * 预算MPC转移项目 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ProjectTransferServiceImpl extends BaseServiceImpl<ProjectTransfer, Long> implements ProjectTransferService {

    private final ProjectTransferRepository  projectTransferRepository;

    public ProjectTransferServiceImpl(ProjectTransferRepository  projectTransferRepository) {
        super(projectTransferRepository);
        this.projectTransferRepository = projectTransferRepository;
    }
}
