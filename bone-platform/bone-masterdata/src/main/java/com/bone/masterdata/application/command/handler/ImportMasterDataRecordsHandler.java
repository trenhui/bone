package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.command.cmd.ImportMasterDataRecordsCommand;
import com.bone.masterdata.domain.gateway.MasterDataExcelImportPort;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "ImportMasterDataRecords",
    description = "批量导入主数据记录",
    inputSchema = "{\"masterDataEntityId\": \"long\", \"file\": \"file\"}",
    outputSchema = "{\"recordIds\": \"array\"}",
    idempotent = false,
    cost = 8,
    retryable = false,
    timeout = 300)
@Component
@RequiredArgsConstructor
public class ImportMasterDataRecordsHandler {
  private final MasterDataRecordRepository recordRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataExcelImportPort masterDataExcelImportPort;

  @Transactional
  public List<Long> handle(ImportMasterDataRecordsCommand cmd) {
    if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
      throw NotFoundException.of("主数据实体不存在");
    }

    List<MasterDataRecord> records =
        masterDataExcelImportPort.parseRecords(
            cmd.getDataStream(), cmd.getOriginalFilename(), cmd.getMasterDataEntityId());

    records.forEach(recordRepository::save);

    return records.stream().map(MasterDataRecord::getId).toList();
  }
}
