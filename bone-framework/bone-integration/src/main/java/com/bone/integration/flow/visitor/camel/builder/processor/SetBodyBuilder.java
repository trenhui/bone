package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.SetBodyNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.Builder.simple;

@Component
public class SetBodyBuilder extends CamelProcessorBuilder<SetBodyNode> {

    @Override
    public void process(SetBodyNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.setBody(simple(node.getBody()));

        context.writeOutput(node.getBody()).writeOutput("'))\n");
    }
        }

