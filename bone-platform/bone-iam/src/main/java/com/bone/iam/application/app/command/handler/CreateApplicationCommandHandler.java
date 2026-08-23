package com.bone.iam.application.app.command.handler;

import com.bone.iam.application.app.command.CreateApplicationCommand;
import com.bone.iam.domain.app.BoneApplication;
import com.bone.iam.domain.app.repository.BoneApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateApplicationCommandHandler {
  private final BoneApplicationRepository boneApplicationRepository;

  @Transactional
  public Long handle(CreateApplicationCommand cmd) {
    BoneApplication app =
        BoneApplication.create(
            cmd.getName(), cmd.getCode(), cmd.getDescription(), cmd.getIcon(), 0L);
    return boneApplicationRepository.save(app);
  }
}
