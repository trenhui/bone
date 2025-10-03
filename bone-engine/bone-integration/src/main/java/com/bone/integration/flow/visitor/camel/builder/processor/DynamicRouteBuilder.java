package com.bone.integration.flow.visitor.camel.builder.processor;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.DynamicRouteNode;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.Builder.simple;

@Component
public class DynamicRouteBuilder extends CamelProcessorBuilder<DynamicRouteNode> {

    @Override
    public void process(DynamicRouteNode node, CamelBuilderContext context) {
//        ProcessorDefinition<?> definition = context.peekDefinition();
//        DynamicRouteLiteFlowProcessor dynamicRouteLiteFlowProcessor = new DynamicRouteLiteFlowProcessor(context.getCamelContext(), node.getDynamicRouteId());
//        definition.process(dynamicRouteLiteFlowProcessor).recipientList(simple("direct:${header._routeCode}"));
//
//        context.writeOutput(".process(dynamicRouteLiteFlowProcessor).recipientList(simple('direct:${header._routeCode}'))\n");
    }
}