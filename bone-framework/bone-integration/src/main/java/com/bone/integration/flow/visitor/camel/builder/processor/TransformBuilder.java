package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.processor.FreemarkerProcessor;
import com.bone.integration.flow.node.TransformNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class TransformBuilder extends CamelProcessorBuilder<TransformNode> {

    @Override
    public void process(TransformNode node, CamelBuilderContext context) {
        String ftlPath = node.getFilePath();
        String templateContent = node.getContent();
        String inputFormat = node.getInputFormat();

        FreemarkerProcessor freemarkerProcessor = new FreemarkerProcessor(ftlPath, templateContent, inputFormat);

        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.process(freemarkerProcessor);

        context.writeOutput(".process(freemarkerProcessor)\n");
    }
}