package com.bone.masterdata.application.command.handler;

import com.bone.core.exception.BizException;
import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "CreateMasterDataEntity",
        description = "创建主数据实体定义",
        inputSchema = "{\"name\": \"string\", \"description\": \"string\", \"category\": \"string\"}",
        outputSchema = "{\"entityId\": \"long\"}",
        idempotent = false,
        cost = 2,
        retryable = true,
        timeout = 15)
@Component
@RequiredArgsConstructor
public class CreateMasterDataEntityHandler {
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateMasterDataEntityCmd cmd) {
        MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());

        boolean exists = QueryBuilder.from(MasterDataEntity.class)
                .where(MasterDataEntity::getName)
                .eq(entityName)
                .exists();
        if (exists) {
            throw BizException.of("主数据实体名称已存在");
        }

        Long entityId = DistributedIdGenerator.generateLongId();
        MasterDataEntity entity = MasterDataEntity.create(
                entityId, entityName, cmd.getDescription(), cmd.getCategory());

        return entityRepository.save(entity);
    }
}
