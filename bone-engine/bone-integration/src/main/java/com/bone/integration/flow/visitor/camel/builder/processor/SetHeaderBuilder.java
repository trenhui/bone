package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.SetHeaderNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.Builder.simple;

@Component
public class SetHeaderBuilder extends CamelProcessorBuilder<SetHeaderNode> {

    @Override
    public void process(SetHeaderNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.setHeader(node.getHeaderName(), simple(node.getHeaderValue()));

        context.writeOutput(".setHeader('").writeOutput(node.getHeaderName())
                .writeOutput("', simple('").writeOutput(node.getHeaderValue()).writeOutput("'))\n");
    }
}
