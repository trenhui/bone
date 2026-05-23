package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "PublishMasterDataRecord",
    description = "发布主数据记录",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 2,
    retryable = false,
    timeout = 20
)
@Component
@RequiredArgsConstructor
public class PublishMasterDataRecordHandler {
    private final MasterDataRecordRepository masterDataRecordRepository;

    @Transactional
    public void handle(Long id) {
        MasterDataRecord record = masterDataRecordRepository.findById(id);
        if (record == null) {
            throw NotFoundException.of("主数据记录不存在");
        }

        record.publish();
        masterDataRecordRepository.update(record);
    }
}
