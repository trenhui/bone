package com.bone.lowcode.integration.core.log;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

@Slf4j
public class ConsoleLogger {
    private String debugConnId;
    @Resource
    private LogSendManager logSendManager;

    public ConsoleLogger(String debugConnId, LogSendManager logSendManager) {
        this.debugConnId = debugConnId;
        this.logSendManager = logSendManager;
    }

    public void info(String content, Object... args) {
        log.info(content, args);
        String message = format(content, args);
        LogEntity logEntity = new LogEntity(debugConnId, message);
        logSendManager.add(logEntity);
    }

    public static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }

        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int start = 0;
        int placeholderIndex;

        while ((placeholderIndex = template.indexOf("{}", start)) != -1) {
            result.append(template, start, placeholderIndex);
            if (argIndex < args.length) {
                result.append(args[argIndex++]);
            } else {
                result.append("{}"); // 参数不足，保留占位符
            }
            start = placeholderIndex + 2; // 跳过 "{}"
        }

        result.append(template.substring(start)); // 追加剩余部分
        return result.toString();
    }
}
