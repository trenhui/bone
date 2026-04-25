package com.bone.masterdata.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCmd;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.infrastructure.util.ExcelUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Capability(
    name = "ImportMasterDataRecords",
    description = "批量导入主数据记录",
    inputSchema = "{\"masterDataEntityId\": \"long\", \"file\": \"file\"}",
    outputSchema = "{\"recordIds\": \"array\"}",
    idempotent = false,
    cost = 8,
    retryable = false,
    timeout = 300
)
@Component
@RequiredArgsConstructor
public class ImportMasterDataRecordsHandler {
    private final MasterDataRecordRepository recordRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public List<Long> handle(ImportMasterDataRecordsCmd cmd) {
        if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        List<MasterDataRecord> records = ExcelUtils.parseExcelFile(
                cmd.getFile(),
                cmd.getMasterDataEntityId()
        );

        records.forEach(record -> {
            if (record.getId() == null) {
                recordRepository.save(record);
            } else {
                recordRepository.update(record);
            }
        });

        return records.stream()
                .map(MasterDataRecord::getId)
                .toList();
    }
}
