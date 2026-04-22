package com.bone.engine.extension.core.metrics;

import java.util.Map;

/**
 * 扩展点告警服务SPI
 * 
 * 支持多种告警方式：日志、邮件、短信、钉钉、企业微信等
 */
public interface ExtensionAlarmService {

    /**
     * 发送告警
     * 
     * @param level 告警级别
     * @param message 告警消息
     * @param context 上下文信息
     */
    void sendAlarm(AlarmLevel level, String message, Map<String, Object> context);

    /**
     * 告警级别
     */
    enum AlarmLevel {
        /** 信息级别 */
        INFO,
        /** 警告级别 */
        WARNING,
        /** 错误级别 */
        ERROR,
        /** 严重级别 */
        CRITICAL
    }
}
