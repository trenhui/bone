package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.application.service.IDirectNodeManagerService;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.processor.DynamicDirectUrlProcessor;
import com.bone.integration.flow.node.RecipientListNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static org.apache.camel.builder.Builder.simple;

@Component
public class RecipientListBuilder extends CamelProcessorBuilder<RecipientListNode> {
    @Resource
    private IDirectNodeManagerService iDirectNodeManagerService;

    @Override
    public void process(RecipientListNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        DynamicDirectUrlProcessor dynamicDirectUrlProcessor = new DynamicDirectUrlProcessor(context, iDirectNodeManagerService, node);
        definition.process(dynamicDirectUrlProcessor);
        context.writeOutput(".process(dynamicDirectUrlProcessor)");

        RecipientListDefinition<?> recipientListDefinition = definition.recipientList(simple("${header.dynamic_direct_uri}"));
        context.writeOutput(".recipientList(simple(\"${header.dynamic_direct_uri}\"))");

        if (Boolean.TRUE.equals(node.getParallelProcessing())) {
            recipientListDefinition.parallelProcessing();
            context.writeOutput(".parallelProcessing()");
        }

        context.writeOutput("\n");
    }
}