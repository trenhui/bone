package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.ChoiceNode;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class ChoiceBuilder extends CamelProcessorBuilder<ChoiceNode> {

    @Override
    public void process(ChoiceNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        ChoiceDefinition choiceDefinition = definition.choice();
        context.pushDefinition(choiceDefinition);

        context.writeOutput(".choice()\n");
    }

    @Override
    public void leave(ChoiceNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        definition.end();
        context.popDefinition();

        context.writeOutput(".end()\n");
    }
}
