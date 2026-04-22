package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.UpdateMasterDataFieldCmd;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.field.MasterDataField;
import com.bone.masterdata.domain.model.field.vo.MasterDataFieldId;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMasterDataFieldHandler {
    private final MasterDataFieldRepository masterDataFieldRepository;

    @Transactional
    public void handle(UpdateMasterDataFieldCmd cmd) {
        MasterDataField field = masterDataFieldRepository.findById(MasterDataFieldId.of(cmd.getId()));
        if (field == null) {
            throw new NotFoundException("主数据字段不存在");
        }

        field.update(cmd.getName(), cmd.getType(), cmd.getLength(), cmd.getRequired(),
                     cmd.getDefaultValue(), cmd.getDescription(), cmd.getSortOrder());
        masterDataFieldRepository.update(field);
    }
}
