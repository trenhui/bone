package com.bone.iam.application.command.handler;

import com.bone.iam.application.command.cmd.AssignPermissionCmd;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssignPermissionHandler {
    @Transactional
    public void handle(AssignPermissionCmd cmd) {
        // 这里需要实现角色权限分配逻辑
        // 由于使用SDK动态代理，具体实现会由SDK处理
    }
}