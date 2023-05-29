package com.netease.mis.bsm.budget.application;

import com.netease.mis.bsm.budget.domain.model.Attachment;
import com.netease.mis.bsm.budget.domain.service.AttachmentService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 单据附件 ApplicationService
 *
 * @author 梅山源码
 */
public class AttachmentApplicationService {

    @Resource
    private AttachmentService attachmentService;

    /**
     * 创建单据附件
     *
     * @param attachment 单据附件
     * @return Id
     */
    public Long create(@Valid Attachment attachment) {
        return attachmentService.create(attachment).getId();
    }
}
