package com.bone.system.application.query.handler;

import com.bone.system.domain.model.console.QuickAction;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 控制台快捷入口（与 {@code bone-shell} 微应用路由一致）。
 *
 * <p>当前为静态配置；接入 {@code cnsl_quick_action} 表后改为按租户/角色筛选（[Target]）。
 */
@Component
public class QuickActionsQueryHandler {

    @Transactional(readOnly = true)
    public List<QuickAction> handle() {
        return List.of(
                quick("iam", "账号权限管理", "/iam", "UserOutlined"),
                quick("metadata", "元数据管理", "/metadata", "DatabaseOutlined"),
                quick("masterdata", "主数据管理", "/masterdata", "DatabaseOutlined"),
                quick("integration", "集成管理", "/integration", "LinkOutlined"),
                quick("system", "系统管理", "/system", "SettingOutlined"),
                quick("extension", "扩展管理", "/extension", "AppstoreOutlined"));
    }

    private static QuickAction quick(String id, String title, String path, String icon) {
        return QuickAction.builder().id(id).title(title).path(path).icon(icon).build();
    }
}
