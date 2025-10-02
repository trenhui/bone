package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.core.log.LogSendManager;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.LogNode;
import com.bone.lowcode.integration.processor.LogDebugProcessor;
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
