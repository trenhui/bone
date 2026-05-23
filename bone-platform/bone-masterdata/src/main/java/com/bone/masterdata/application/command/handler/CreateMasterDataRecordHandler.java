package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataRecordCommand;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateMasterDataRecord",
    description = "创建主数据记录",
    inputSchema = "{\"masterDataEntityId\": \"long\", \"data\": \"object\"}",
    outputSchema = "{\"recordId\": \"long\"}",
    idempotent = false,
    cost = 2,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class CreateMasterDataRecordHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;
    private final MasterDataEntityRepository masterDataEntityRepository;

    @Transactional
    public Long handle(CreateMasterDataRecordCommand cmd) {
        MasterDataEntity entity = masterDataEntityRepository.findById(cmd.getMasterDataEntityId());
        if (entity == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        Long recordId = DistributedIdGenerator.generateLongId();
        MasterDataRecord record = MasterDataRecord.create(recordId, cmd.getMasterDataEntityId(), cmd.getData());
        masterDataRecordRepository.save(record);
        return record.getId();
    }
}
