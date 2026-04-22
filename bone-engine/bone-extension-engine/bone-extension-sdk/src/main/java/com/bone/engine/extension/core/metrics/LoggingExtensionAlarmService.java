package com.bone.engine.extension.core.metrics;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 日志告警服务默认实现
 * 
 * 将告警消息输出到日志系统
 */
@Slf4j
public class LoggingExtensionAlarmService implements ExtensionAlarmService {

    @Override
    public void sendAlarm(AlarmLevel level, String message, Map<String, Object> context) {
        String logMessage = buildLogMessage(level, message, context);
        
        switch (level) {
            case INFO:
                log.info(logMessage);
                break;
            case WARNING:
                log.warn(logMessage);
                break;
            case ERROR:
            case CRITICAL:
                log.error(logMessage);
                break;
        }
    }

    private String buildLogMessage(AlarmLevel level, String message, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Extension Alarm] [").append(level).append("] ");
        sb.append(message);
        
        if (context != null && !context.isEmpty()) {
            sb.append(" - Context: ");
            sb.append(context);
        }
        
        return sb.toString();
    }
}
