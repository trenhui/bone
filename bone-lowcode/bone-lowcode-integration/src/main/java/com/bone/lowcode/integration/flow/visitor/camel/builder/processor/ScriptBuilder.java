package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.node.ScriptNode;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.processor.ScriptProcessor;
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
