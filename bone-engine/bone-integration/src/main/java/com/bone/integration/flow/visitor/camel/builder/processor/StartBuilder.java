package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.StartNode;
import org.springframework.stereotype.Component;

@Component
public class StartBuilder extends CamelProcessorBuilder<StartNode> {

    @Override
    public void process(StartNode node, CamelBuilderContext context) {

    }
}
