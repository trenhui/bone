package com.bone.masterdata.application.command.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublishMasterDataEntityHandler {
    private final MasterDataEntityRepository masterDataEntityRepository;

    @Transactional
    public void handle(Long id) {
        MasterDataEntity entity = masterDataEntityRepository.findById(MasterDataEntityId.of(id));
        if (entity == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        entity.publish();
        masterDataEntityRepository.update(entity);
    }
}
