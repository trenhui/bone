package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.command.cmd.DisableMasterDataEntityCommand;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "DisableMasterDataEntity",
    description = "停用/启用主数据实体",
    inputSchema = "{\"id\": \"long\", \"disabled\": \"boolean\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15)
@Component
@RequiredArgsConstructor
public class DisableMasterDataEntityHandler {
  private final MasterDataEntityRepository masterDataEntityRepository;

  @Transactional
  public void handle(DisableMasterDataEntityCommand cmd) {
    MasterDataEntity entity = masterDataEntityRepository.findById(cmd.getId());
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    if (cmd.isDisabled()) {
      entity.disable();
    } else {
      entity.enable();
    }
    masterDataEntityRepository.update(entity);
  }
}
