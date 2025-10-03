package com.bone.integration.processor;

import com.bone.integration.core.log.LogEntity;
import com.bone.integration.core.log.LogSendManager;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.lang3.StringUtils;

import static org.apache.camel.builder.Builder.simple;


@Data
public class LogDebugProcessor implements Processor {
    private String expression;
    private LogSendManager logSendManager;

    public LogDebugProcessor(String expression, LogSendManager logSendManager) {
        this.expression = expression;
        this.logSendManager = logSendManager;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String debugConnId = exchange.getIn().getHeader("debugConnId", String.class);
        if (StringUtils.isBlank(debugConnId)) {
            return;
        }

        String message = simple(expression).evaluate(exchange, String.class);
        LogEntity logEntity = new LogEntity(debugConnId, message);
        logSendManager.add(logEntity);
    }
}
