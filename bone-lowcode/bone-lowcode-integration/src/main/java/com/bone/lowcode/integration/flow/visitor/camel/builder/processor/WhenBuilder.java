package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.WhenNode;
import org.apache.camel.model.ChoiceDefinition;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.Builder.simple;

@Component
public class WhenBuilder extends CamelProcessorBuilder<WhenNode> {

    @Override
    public void process(WhenNode node, CamelBuilderContext context) {
        ChoiceDefinition choiceDefinition = context.peekDefinition();
        choiceDefinition.when(simple(node.getCondition()));

        context.writeOutput(".when(simple(\"").writeOutput(node.getCondition()).writeOutput("\"))\n");
    }
}
