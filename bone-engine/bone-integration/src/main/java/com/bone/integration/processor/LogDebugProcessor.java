package com.bone.integration.processor;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;

import static org.apache.camel.builder.Builder.simple;

/**
 * 日志调试处理器
 * 用于在集成流程中记录调试信息
 */
@Data
@Slf4j
public class LogDebugProcessor implements Processor {
    private String expression;

    public LogDebugProcessor(String expression) {
        this.expression = expression;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String debugConnId = exchange.getIn().getHeader("debugConnId", String.class);
        if (StringUtils.isBlank(debugConnId)) {
            return;
        }

        try {
            String message = simple(expression).evaluate(exchange, String.class);
            log.debug("Debug connection [{}]: {}", debugConnId, message);
        } catch (Exception e) {
            // 捕获并记录异常，不中断流程
            log.warn("Failed to evaluate debug expression [{}] for connection [{}]", 
                     expression, debugConnId, e);
        }
    }
}
