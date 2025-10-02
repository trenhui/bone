package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.node.YingDaEncryptNode;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.processor.YingDaEncrypteProcessor;
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
