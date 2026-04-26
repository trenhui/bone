package com.bone.lowcode.infra.domain.service;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.UploadAttachmentDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.UploadAttachmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UploadAttachmentService {

    @Autowired
    private UploadAttachmentMapper uploadAttachmentMapper;

    public UploadAttachmentDO getDOById(Long id) {
        return uploadAttachmentMapper.selectById(id);
    }

    public boolean updateById(UploadAttachmentDO uploadAttachmentDO) {
        return uploadAttachmentMapper.updateById(uploadAttachmentDO) > 0;
    }
}
