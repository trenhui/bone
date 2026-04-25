package com.bone.masterdata.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCmd;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.entity.vo.FieldCode;
import com.bone.masterdata.domain.entity.vo.FieldName;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateMasterDataField",
    description = "创建主数据字段定义",
    inputSchema = "{\"masterDataEntityId\": \"long\", \"name\": \"string\", \"type\": \"string\", \"length\": \"int\", \"required\": \"boolean\"}",
    outputSchema = "{\"fieldId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class CreateMasterDataFieldHandler {
    private final MasterDataFieldRepository fieldRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateMasterDataFieldCmd cmd) {
        if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        boolean exists = QueryBuilder.from(MasterDataField.class)
                .where(MasterDataField::getMasterDataEntityId).eq(cmd.getMasterDataEntityId())
                .and(MasterDataField::getName).eq(cmd.getName())
                .exists();
        if (exists) {
            throw BizException.of("字段名称已存在");
        }

        Long fieldId = DistributedIdGenerator.generateLongId();
        MasterDataField field = MasterDataField.create(
                fieldId,
                cmd.getMasterDataEntityId(),
                FieldName.of(cmd.getName()),
                FieldCode.of(cmd.getName()),
                cmd.getType(),
                cmd.getLength(),
                cmd.getRequired(),
                cmd.getDefaultValue(),
                cmd.getDescription(),
                0
        );

        return fieldRepository.save(field).getId();
    }
}
