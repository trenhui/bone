package com.bone.iam.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.command.cmd.CreateMenuCommand;
import com.bone.iam.domain.menu.Menu;
import com.bone.iam.domain.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateMenu",
    description = "创建菜单节点",
    inputSchema =
        "{\"name\": \"string\", \"parentId\": \"long\", \"path\": \"string\", \"icon\": \"string\", \"orderNo\": \"int\", \"permission\": \"string\", \"type\": \"int\"}",
    outputSchema = "{\"menuId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
public class CreateMenuCommandHandler {

  private final MenuRepository menuRepository;

  @Transactional
  public Long handle(CreateMenuCommand cmd) {
    Long tenantId = cmd.getTenantId();
    if (tenantId == null) {
      tenantId = TenantContext.getTenantIdAsLong();
    }
    if (tenantId == null) {
      tenantId = 0L;
    }
    Menu menu =
        Menu.create(
            cmd.getName(),
            cmd.getParentId(),
            cmd.getPath(),
            cmd.getIcon(),
            cmd.getOrderNo(),
            cmd.getPermission(),
            cmd.getType(),
            tenantId);
    menuRepository.save(menu);
    return menu.getId();
  }
}
