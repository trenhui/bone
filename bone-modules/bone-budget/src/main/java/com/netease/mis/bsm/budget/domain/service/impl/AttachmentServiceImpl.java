package com.netease.mis.bsm.budget.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.netease.mis.bsm.budget.domain.repository.AttachmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.netease.mis.bsm.budget.domain.model.Attachment;
import com.netease.mis.bsm.budget.domain.service.AttachmentService;

/**
 * 单据附件 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class AttachmentServiceImpl extends BaseServiceImpl<Attachment, Long> implements AttachmentService {

    private final AttachmentRepository  attachmentRepository;

    public AttachmentServiceImpl(AttachmentRepository  attachmentRepository) {
        super(attachmentRepository);
        this.attachmentRepository = attachmentRepository;
    }
}
