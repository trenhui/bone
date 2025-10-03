package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.MarshalNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class MarshalBuilder extends CamelProcessorBuilder<MarshalNode> {

    @Override
    public void process(MarshalNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();

        String type = node.getType().toLowerCase();
        switch (type) {
            case "json":
                definition.marshal().json(JsonLibrary.Jackson);
                context.writeOutput(".marshal().json(JsonLibrary.Jackson)\n");
                break;
            case "xml":
                definition.marshal().jacksonXml();
                context.writeOutput(".marshal().jacksonXml()\n");
                break;
            default:
                throw new RuntimeException("Unmarshal type not supported: " + type);
        }
    }
}
