package com.bone.masterdata.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataRecordCmd;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateMasterDataRecord",
    description = "更新主数据记录",
    inputSchema = "{\"id\": \"long\", \"data\": \"object\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15
)
@Component
@RequiredArgsConstructor
public class UpdateMasterDataRecordHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;

    @Transactional
    public void handle(UpdateMasterDataRecordCmd cmd) {
        MasterDataRecord record = masterDataRecordRepository.findById(cmd.getId());
        if (record == null) {
            throw NotFoundException.of("主数据记录不存在");
        }

        record.update(cmd.getData());
        masterDataRecordRepository.update(record);
    }
}
