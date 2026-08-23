package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.iam.application.command.cmd.UpdateMenuCommand;
import com.bone.iam.domain.menu.Menu;
import com.bone.iam.domain.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "UpdateMenu",
    description = "更新菜单节点",
    inputSchema =
        "{\"id\": \"long\", \"name\": \"string\", \"parentId\": \"long\", \"path\": \"string\", \"icon\": \"string\", \"orderNo\": \"int\", \"permission\": \"string\", \"type\": \"int\"}",
    outputSchema = "{\"menuId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class UpdateMenuCommandHandler {

  private final MenuRepository menuRepository;

  @Transactional
  public Long handle(UpdateMenuCommand cmd) {
    Menu menu = menuRepository.findById(cmd.getId());
    if (menu == null) {
      throw new IllegalArgumentException("菜单不存在: " + cmd.getId());
    }
    menu.update(
        cmd.getName(),
        cmd.getParentId(),
        cmd.getPath(),
        cmd.getIcon(),
        cmd.getOrderNo(),
        cmd.getPermission(),
        cmd.getType());
    menuRepository.update(menu);
    return menu.getId();
  }
}
