package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMasterDataEntityHandler {
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(CreateMasterDataEntityCmd cmd) {
        MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());

        if (entityRepository.existsByName(cmd.getName())) {
            throw BizException.of("主数据实体名称已存在");
        }
        
        MasterDataEntity entity = MasterDataEntity.create(
            entityName,
            cmd.getDescription(),
            cmd.getCategory()
        );
        
        return entityRepository.save(entity).getValue();
    }
}