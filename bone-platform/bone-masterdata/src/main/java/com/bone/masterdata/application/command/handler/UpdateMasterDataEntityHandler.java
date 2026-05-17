package com.bone.masterdata.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateMasterDataEntity",
    description = "更新主数据实体定义",
    inputSchema = "{\"id\": \"long\", \"name\": \"string\", \"description\": \"string\", \"category\": \"string\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class UpdateMasterDataEntityHandler {
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public void handle(UpdateMasterDataEntityCmd cmd) {
        MasterDataEntity entity = entityRepository.findById(cmd.getId());
        if (entity == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());

        entity.update(entityName, cmd.getDescription(), cmd.getCategory());
        entityRepository.save(entity);
    }
}
