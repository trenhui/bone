package com.bone.iam.application.app.command.handler;

import com.bone.core.exception.BizException;
import com.bone.iam.application.app.command.UpdateApplicationCommand;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.iam.domain.repository.BoneApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateApplicationCommandHandler {
  private final BoneApplicationRepository boneApplicationRepository;

  @Transactional
  public void handle(UpdateApplicationCommand cmd) {
    BoneApplication app = boneApplicationRepository.findById(cmd.getId());
    if (app == null) throw new BizException(404, "应用不存在");
    app.update(cmd.getName(), cmd.getDescription(), cmd.getIcon(), cmd.getStatus());
    boneApplicationRepository.save(app);
  }
}
