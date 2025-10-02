package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.ElseNode;
import org.apache.camel.model.ChoiceDefinition;
import org.springframework.stereotype.Component;

@Component
public class ElseBuilder extends CamelProcessorBuilder<ElseNode> {

    @Override
    public void process(ElseNode node, CamelBuilderContext context) {
        ChoiceDefinition choiceDefinition = context.peekDefinition();
        choiceDefinition.otherwise();

        context.writeOutput(".otherwise()\n");
    }

}
