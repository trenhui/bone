package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.WebServiceNode;
import com.bone.lowcode.integration.processor.WebServiceProcessor;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class WebServiceBuilder extends CamelProcessorBuilder<WebServiceNode> {

    @Override
    public void process(WebServiceNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        WebServiceProcessor webServiceProcessor = new WebServiceProcessor(node);
        definition.process(webServiceProcessor);

        context.writeOutput(".process(webServiceProcessor)\n");
    }
}
