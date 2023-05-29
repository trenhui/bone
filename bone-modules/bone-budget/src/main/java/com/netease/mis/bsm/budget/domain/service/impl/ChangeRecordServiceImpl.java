package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.ChangeRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.ChangeRecord;
import com.netease.mis.bsm.budget.domain.service.ChangeRecordService;

/**
 * 预算变动记录 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class ChangeRecordServiceImpl extends BaseServiceImpl<ChangeRecord, Long> implements ChangeRecordService {

    private final ChangeRecordRepository  changeRecordRepository;

    public ChangeRecordServiceImpl(ChangeRecordRepository  changeRecordRepository) {
        super(changeRecordRepository);
        this.changeRecordRepository = changeRecordRepository;
    }
}
