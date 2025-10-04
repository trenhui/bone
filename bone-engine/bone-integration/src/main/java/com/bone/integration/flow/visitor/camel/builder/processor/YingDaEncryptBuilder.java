package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.processor.YingDaEncrypteProcessor;
import com.bone.integration.flow.node.YingDaEncryptNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class YingDaEncryptBuilder extends CamelProcessorBuilder<YingDaEncryptNode> {

    @Override
    public void process(YingDaEncryptNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        YingDaEncrypteProcessor yingDaEncrypteProcessor = new YingDaEncrypteProcessor(node);
        definition.process(yingDaEncrypteProcessor);

        context.writeOutput(".process(yingDaEncrypteProcessor)\n");
    }
}
