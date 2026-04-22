package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateMasterDataEntityHandler {
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public void handle(UpdateMasterDataEntityCmd cmd) {
        MasterDataEntity entity = entityRepository.findById(MasterDataEntityId.of(cmd.getId()));
        if (entity == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());

        entity.update(entityName, cmd.getDescription(), cmd.getCategory());
        entityRepository.save(entity);
    }
}