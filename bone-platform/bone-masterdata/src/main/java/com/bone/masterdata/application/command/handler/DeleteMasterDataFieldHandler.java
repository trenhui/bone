package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "DeleteMasterDataField",
    description = "删除主数据字段定义",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15)
@Component
@RequiredArgsConstructor
public class DeleteMasterDataFieldHandler {
  private final MasterDataFieldRepository masterDataFieldRepository;

  @Transactional
  public void handle(Long id) {
    MasterDataField field = masterDataFieldRepository.findById(id);
    if (field == null) {
      throw NotFoundException.of("主数据字段不存在");
    }
    masterDataFieldRepository.deleteById(id);
  }
}
