package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.processor.EnAndDeProcessor;
import com.bone.integration.flow.node.EnAndDeNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class EnAndDeBuilder extends CamelProcessorBuilder<EnAndDeNode> {

    @Override
    public void process(EnAndDeNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        EnAndDeProcessor enAndDeProcessor=new EnAndDeProcessor(node);
        definition.process(enAndDeProcessor);

        context.writeOutput(".process(enAndDeProcessor)\n");
    }
}
