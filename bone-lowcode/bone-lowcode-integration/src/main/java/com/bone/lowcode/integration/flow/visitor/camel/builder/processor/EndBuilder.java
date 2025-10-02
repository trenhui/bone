package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.EndNode;
import org.springframework.stereotype.Component;

@Component
public class EndBuilder extends CamelProcessorBuilder<EndNode> {

    @Override
    public void process(EndNode node, CamelBuilderContext context) {
        
    }
}
