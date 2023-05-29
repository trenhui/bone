package com.bone.module.system.mq.consumer.permission;

import com.bone.module.system.mq.message.permission.RoleMenuRefreshMessage;
import com.bone.module.system.service.permission.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 针对 {@link RoleMenuRefreshMessage} 的消费者
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class RoleMenuRefreshConsumer {

    @Resource
    private PermissionService permissionService;

    @EventListener
    public void execute(RoleMenuRefreshMessage roleMenuRefreshMessage) {
        log.info("[execute][收到 Role 与 Menu 的关联刷新消息]");
        permissionService.initLocalCache();
    }
}
