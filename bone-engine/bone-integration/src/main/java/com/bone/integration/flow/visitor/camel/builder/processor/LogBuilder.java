package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.processor.LogDebugProcessor;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.LogNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class LogBuilder extends CamelProcessorBuilder<LogNode> {

    @Override
    public void process(LogNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.log(node.getLog());
        LogDebugProcessor logDebugProcessor = new LogDebugProcessor(node.getLog());
        definition.process(logDebugProcessor);

        context.writeOutput(".log(\"").writeOutput(node.getLog()).writeOutput(")\n")
                .writeOutput(".process(logDebugProcessor)\n");
    }
}
