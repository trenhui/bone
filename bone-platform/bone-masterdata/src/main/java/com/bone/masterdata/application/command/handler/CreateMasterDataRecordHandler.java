package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCmd;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateMasterDataRecordHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;
    private final MasterDataEntityRepository masterDataEntityRepository;

    @Transactional
    public Long handle(CreateMasterDataRecordCmd cmd) {
        MasterDataEntityId entityId = MasterDataEntityId.of(cmd.getMasterDataEntityId());
        MasterDataEntity entity = masterDataEntityRepository.findById(entityId);
        if (entity == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        MasterDataRecord record = MasterDataRecord.create(entityId, cmd.getData());
        masterDataRecordRepository.save(record);
        return record.getId().getValue();
    }
}
