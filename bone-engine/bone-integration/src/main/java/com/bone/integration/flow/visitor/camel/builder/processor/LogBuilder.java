package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.processor.LogDebugProcessor;
import com.bone.integration.core.log.LogSendManager;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.LogNode;
import jakarta.annotation.Resource;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class LogBuilder extends CamelProcessorBuilder<LogNode> {
    @Resource
    private LogSendManager logSendManager;

    @Override
    public void process(LogNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.log(node.getLog());
        LogDebugProcessor logDebugProcessor = new LogDebugProcessor(node.getLog(), logSendManager);
        definition.process(logDebugProcessor);

        context.writeOutput(".log(\"").writeOutput(node.getLog()).writeOutput("\")\n")
                .writeOutput(".process(logDebugProcessor)\n");
    }
}
