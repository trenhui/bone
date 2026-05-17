package com.bone.integration.application.event.support;

import com.bone.integration.application.event.port.IntegrationEventNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 集成事件后续动作门面：统一标题/业务 ID 拼装，委托 {@link IntegrationEventNotifier}。
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventFollowUp {

    private final IntegrationEventNotifier eventNotifier;

    public void notifyInfo(String action, String detail, String businessId) {
        eventNotifier.sendInfo(action, humanTitle(action), detail, businessId);
    }

    public void notifyHigh(String action, String detail, String businessId) {
        eventNotifier.sendHigh(action, humanTitle(action), detail, businessId);
    }

    private static String humanTitle(String action) {
        return switch (action) {
            case "connector.created.notify" -> "连接器已创建";
            case "connector.test.failed.alert" -> "连接器测试失败";
            case "flow.created.notify" -> "集成流程已创建";
            case "flow.activated.notify" -> "集成流程已激活";
            case "flow.execution.failed.alert" -> "流程执行失败";
            case "execution.started.notify" -> "流程执行已开始";
            case "execution.failed.alert" -> "流程执行失败";
            default -> "集成平台通知";
        };
    }
}
