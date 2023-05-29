package com.bone.module.system.mq.consumer.permission;

import com.bone.module.system.mq.message.permission.RoleRefreshMessage;
import com.bone.module.system.service.permission.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 针对 {@link RoleRefreshMessage} 的消费者
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class RoleRefreshConsumer {

    @Resource
    private RoleService roleService;

    @EventListener
    public void execute(RoleRefreshMessage message) {
        log.info("[execute][收到 Role 刷新消息]");
        roleService.initLocalCache();
    }

}
