package com.bone.iam.application.app.command.handler;

import com.bone.iam.domain.app.repository.BoneApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteApplicationCommandHandler {
  private final BoneApplicationRepository boneApplicationRepository;

  @Transactional
  public void handle(Long id) {
    boneApplicationRepository.deleteById(id);
  }
}
