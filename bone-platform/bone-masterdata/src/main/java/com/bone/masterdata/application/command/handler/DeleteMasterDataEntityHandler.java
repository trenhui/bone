package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 删除主数据实体命令处理器（软删） */
@Capability(
    name = "DeleteMasterDataEntity",
    description = "删除主数据实体",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 3,
    retryable = false,
    timeout = 30)
@Component
@RequiredArgsConstructor
public class DeleteMasterDataEntityHandler {

  private final MasterDataEntityRepository masterDataEntityRepository;

  @Transactional
  public void handle(Long id) {
    MasterDataEntity entity = masterDataEntityRepository.findById(id);
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    masterDataEntityRepository.deleteById(id);
  }
}
