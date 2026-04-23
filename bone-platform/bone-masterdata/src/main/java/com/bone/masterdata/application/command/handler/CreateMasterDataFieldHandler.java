package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.CreateMasterDataFieldCmd;
import com.bone.masterdata.domain.model.field.MasterDataField;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.field.vo.FieldCode;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMasterDataFieldHandler {
    private final MasterDataFieldRepository fieldRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateMasterDataFieldCmd cmd) {
        // 验证主数据实体存在
        MasterDataEntityId entityId = MasterDataEntityId.of(cmd.getMasterDataEntityId());
        if (entityRepository.findById(entityId) == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        // 验证字段名称是否已存在
        boolean exists = QueryBuilder.from(MasterDataField.class)
                .where(MasterDataField::getMasterDataEntityId).eq(entityId.getValue())
                .and(MasterDataField::getName).eq(cmd.getName())
                .exists();
        if (exists) {
            throw BizException.of("字段名称已存在");
        }

        MasterDataField field = MasterDataField.create(
            entityId,
            FieldName.of(cmd.getName()),
            FieldCode.of(cmd.getName()),
            cmd.getType(),
            cmd.getLength(),
            cmd.getRequired(),
            cmd.getDefaultValue(),
            cmd.getDescription(),
            0 // 默认排序
        );

        return fieldRepository.save(field).getValue();
    }
}