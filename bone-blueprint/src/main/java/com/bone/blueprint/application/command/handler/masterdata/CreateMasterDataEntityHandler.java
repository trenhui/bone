package com.bone.blueprint.application.command.handler.masterdata;

import com.bone.blueprint.application.command.cmd.masterdata.CreateMasterDataEntityCmd;
import com.bone.blueprint.domain.model.masterdata.MasterDataEntity;
import com.bone.blueprint.domain.repository.masterdata.MasterDataEntityRepository;
import com.bone.blueprint.adapter.web.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMasterDataEntityHandler {
    private final MasterDataEntityRepository masterDataEntityRepository;
    
    @Transactional
    public Long handle(CreateMasterDataEntityCmd cmd) {
        // 检查实体名称是否已存在
        if (masterDataEntityRepository.existsByName(cmd.getName())) {
            throw new InvalidRequestException("主数据实体名称已存在");
        }
        
        // 创建主数据实体
        MasterDataEntity entity = MasterDataEntity.create(cmd.getName(), cmd.getDescription(), cmd.getCategory());
        masterDataEntityRepository.save(entity);
        
        return entity.getId();
    }
}
