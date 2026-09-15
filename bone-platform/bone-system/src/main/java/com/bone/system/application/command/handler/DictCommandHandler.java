package com.bone.system.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.cmd.CreateDictCommand;
import com.bone.system.application.command.cmd.DeleteDictCommand;
import com.bone.system.application.command.cmd.UpdateDictCommand;
import com.bone.system.application.query.DictUniquenessQuery;
import com.bone.system.common.exception.NotFoundException;
import com.bone.system.domain.dict.SysDict;
import com.bone.system.domain.dict.vo.DictType;
import com.bone.system.domain.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "ManageDict",
    description = "字典增删改",
    inputSchema =
        "{\"type\": \"string\", \"typeName\": \"string\", \"code\": \"string\", \"label\": \"string\", \"value\": \"string\", \"sort\": \"int\", \"status\": \"int\"}",
    outputSchema = "{\"dictId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
@Transactional
public class DictCommandHandler {
  private final SysDictRepository sysDictRepository;
  private final DictUniquenessQuery dictUniquenessQuery;

  public Long create(CreateDictCommand cmd) {
    DictType type = DictType.of(cmd.getType());
    if (dictUniquenessQuery.existsByTypeAndCode(type, cmd.getCode())) {
      throw BizException.of("字典项已存在: " + type.value() + "/" + cmd.getCode());
    }
    Long id = DistributedIdGenerator.generateLongId();
    SysDict dict =
        SysDict.create(
            id,
            type,
            cmd.getTypeName(),
            cmd.getCode(),
            cmd.getLabel(),
            cmd.getValue(),
            cmd.getSort(),
            cmd.getStatus());
    sysDictRepository.save(dict);
    return dict.getId();
  }

  public void update(UpdateDictCommand cmd) {
    SysDict dict = sysDictRepository.findById(cmd.getId());
    if (dict == null) {
      throw new NotFoundException("字典项不存在: " + cmd.getId());
    }
    dict.update(cmd.getTypeName(), cmd.getLabel(), cmd.getValue(), cmd.getSort(), cmd.getStatus());
    sysDictRepository.save(dict);
  }

  public void delete(DeleteDictCommand cmd) {
    SysDict dict = sysDictRepository.findById(cmd.getId());
    if (dict != null) {
      sysDictRepository.deleteById(dict.getId());
    }
  }
}
