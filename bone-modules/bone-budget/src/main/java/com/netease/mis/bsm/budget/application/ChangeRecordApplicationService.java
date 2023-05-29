package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.ChangeRecord;
import com.netease.mis.bsm.budget.domain.service.ChangeRecordService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 预算变动记录 ApplicationService
 *
 * @author 梅山源码
 */
public class ChangeRecordApplicationService {

    @Resource
    private ChangeRecordService changeRecordService;

    /**
     * 创建预算变动记录
     *
     * @param changeRecord 预算变动记录
     * @return Id
     */
    public Long create(@Valid ChangeRecord changeRecord) {
        return changeRecordService.create(changeRecord).getId();
    }
}
