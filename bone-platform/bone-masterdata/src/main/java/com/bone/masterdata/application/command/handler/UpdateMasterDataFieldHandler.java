package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataFieldCommand;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateMasterDataField",
    description = "更新主数据字段定义",
    inputSchema = "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"length\": \"int\", \"required\": \"boolean\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class UpdateMasterDataFieldHandler {
    private final MasterDataFieldRepository masterDataFieldRepository;

    @Transactional
    public void handle(UpdateMasterDataFieldCommand cmd) {
        MasterDataField field = masterDataFieldRepository.findById(cmd.getId());
        if (field == null) {
            throw NotFoundException.of("主数据字段不存在");
        }

        field.update(
                FieldName.of(cmd.getName()),
                cmd.getType(),
                cmd.getLength(),
                cmd.getRequired(),
                cmd.getDefaultValue(),
                cmd.getDescription(),
                cmd.getSortOrder());
        masterDataFieldRepository.update(field);
    }
}
