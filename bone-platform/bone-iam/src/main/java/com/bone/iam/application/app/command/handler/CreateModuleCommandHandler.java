package com.bone.iam.application.app.command.handler;

import com.bone.iam.application.app.command.CreateModuleCommand;
import com.bone.iam.domain.app.BoneModule;
import com.bone.iam.domain.app.repository.BoneModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateModuleCommandHandler {
  private final BoneModuleRepository boneModuleRepository;

  @Transactional
  public Long handle(CreateModuleCommand cmd) {
    BoneModule mod =
        BoneModule.create(cmd.getAppId(), cmd.getName(), cmd.getCode(), cmd.getDescription(), 0L);
    return boneModuleRepository.save(mod);
  }
}
