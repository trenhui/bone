package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除主数据记录命令处理器（软删） */
@Capability(
    name = "DeleteMasterDataRecord",
    description = "删除主数据记录",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 3,
    retryable = false,
    timeout = 30)
@Component
@RequiredArgsConstructor
public class DeleteMasterDataRecordHandler {

  private final MasterDataRecordRepository masterDataRecordRepository;

  @Transactional
  public void handle(Long id) {
    MasterDataRecord record = masterDataRecordRepository.findById(id);
    if (record == null) {
      throw NotFoundException.of("主数据记录不存在");
    }
    masterDataRecordRepository.deleteById(id);
  }
}
