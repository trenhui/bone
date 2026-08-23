package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.DeleteMenuCommand;
import com.bone.iam.domain.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "DeleteMenu",
    description = "删除菜单节点",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"id\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = false,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class DeleteMenuCommandHandler {

  private final MenuRepository menuRepository;

  @Transactional
  public Long handle(DeleteMenuCommand cmd) {
    if (menuRepository.findById(cmd.getId()) == null) {
      throw new IllegalArgumentException("菜单不存在: " + cmd.getId());
    }
    menuRepository.delete(cmd.getId());
    return cmd.getId();
  }
}
