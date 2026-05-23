package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "PublishMasterDataEntity",
    description = "发布主数据实体",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 3,
    retryable = false,
    timeout = 30
)
@Component
@RequiredArgsConstructor
public class PublishMasterDataEntityHandler {
    private final MasterDataEntityRepository masterDataEntityRepository;

    @Transactional
    public void handle(Long id) {
        MasterDataEntity entity = masterDataEntityRepository.findById(id);
        if (entity == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        entity.publish();
        masterDataEntityRepository.update(entity);
    }
}
