package com.bone.iam.application.app.command.handler;

import com.bone.core.exception.BizException;
import com.bone.iam.application.app.command.UpdateModuleCommand;
import com.bone.iam.domain.app.BoneModule;
import com.bone.iam.domain.app.repository.BoneModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateModuleCommandHandler {
  private final BoneModuleRepository boneModuleRepository;

  @Transactional
  public void handle(UpdateModuleCommand cmd) {
    BoneModule mod = boneModuleRepository.findById(cmd.getId());
    if (mod == null) throw new BizException(404, "模块不存在");
    mod.update(cmd.getName(), cmd.getDescription(), cmd.getStatus());
    boneModuleRepository.save(mod);
  }
}
