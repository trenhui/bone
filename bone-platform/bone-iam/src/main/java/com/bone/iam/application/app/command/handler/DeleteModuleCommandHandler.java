package com.bone.iam.application.app.command.handler;

import com.bone.iam.domain.app.repository.BoneModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteModuleCommandHandler {
  private final BoneModuleRepository boneModuleRepository;

  @Transactional
  public void handle(Long id) {
    boneModuleRepository.deleteById(id);
  }
}
