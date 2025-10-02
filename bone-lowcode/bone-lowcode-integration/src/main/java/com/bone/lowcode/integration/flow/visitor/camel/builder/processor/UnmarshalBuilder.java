package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.UnmarshalNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class UnmarshalBuilder extends CamelProcessorBuilder<UnmarshalNode> {

    @Override
    public void process(UnmarshalNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();

        String type = node.getType().toLowerCase();
        switch (type) {
            case "json":
                definition.unmarshal().json(JsonLibrary.Jackson);
                context.writeOutput(".unmarshal().json(JsonLibrary.Jackson)\n");
                break;
            case "xml":
                definition.unmarshal().jacksonXml();
                context.writeOutput(".unmarshal().jacksonXml()\n");
                break;
            default:
                throw new RuntimeException("Unmarshal type not supported: " + type);
        }
    }
}
