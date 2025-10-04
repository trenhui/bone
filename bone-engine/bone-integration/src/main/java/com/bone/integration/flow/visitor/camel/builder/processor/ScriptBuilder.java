package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.processor.ScriptProcessor;
import com.bone.integration.flow.node.ScriptNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

@Component
public class ScriptBuilder extends CamelProcessorBuilder<ScriptNode> {

    @Override
    public void process(ScriptNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        ScriptProcessor scriptProcessor=new ScriptProcessor(node);
        definition.process(scriptProcessor);

        context.writeOutput(".process(scriptProcessor)\n");
    }
}
