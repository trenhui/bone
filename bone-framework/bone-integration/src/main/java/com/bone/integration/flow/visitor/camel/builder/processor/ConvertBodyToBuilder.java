package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.processor.StringToMapProcessor;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.ConvertBodyToNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class ConvertBodyToBuilder extends CamelProcessorBuilder<ConvertBodyToNode> {
    @Resource
    private StringToMapProcessor stringToMapProcessor;

    @Override
    public void process(ConvertBodyToNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        String type = node.getType();
        switch (type) {
            case "String":
            case "string":
                definition.convertBodyTo(String.class);
                context.writeOutput(".convertBodyTo(String.class)\n");
                break;
            case "Map":
                definition.process(stringToMapProcessor);
                context.writeOutput(".process(stringToMapProcessor)\n");

                break;
            default:
                throw new RuntimeException("ConvertBodyToComponent Unsupported type: " + type);
        }
    }
}
