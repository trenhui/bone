package com.bone.lowcode.integration.flow.visitor.camel.builder.component;


import com.bone.lowcode.integration.flow.node.GraphNode;
import com.bone.lowcode.integration.flow.visitor.camel.builder.CamelNodeBuilder;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

import java.util.function.Consumer;

@Slf4j
public abstract class CamelComponentBuilder<T extends GraphNode> implements CamelNodeBuilder<T> {

    private boolean hasFromNode(CamelBuilderContext context) {
        Boolean hasFromNode = context.getProperty("hasFromNode");
        if (!Boolean.TRUE.equals(hasFromNode)) {
            context.addProperty("hasFromNode", true);
            return false;
        } else {
            return true;
        }
    }

    public ProcessorDefinition<?> build(String id, String uri, CamelBuilderContext context) {
        return build(id, uri, context, null);
    }

    public ProcessorDefinition<?> build(String id, String uri, CamelBuilderContext context,
                                        Consumer<ProcessorDefinition<?>> beforeCallable) {
        RouteBuilder builder = context.getRouteBuilder();
        RouteDefinition routeDefinition;
        if (this.hasFromNode(context)) {
            ProcessorDefinition<?> definition = context.peekDefinition();
            if (beforeCallable != null) {
                beforeCallable.accept(definition);
            }

            definition.to(uri);
            context.writeOutput(".to('").writeOutput(uri).writeOutput("')\n");

            return definition;
        } else {
            routeDefinition = builder.from(uri).routeId(id);
            if (beforeCallable != null) {
                beforeCallable.accept(routeDefinition);
            }

            routeDefinition.process(exchange -> exchange.getIn().setHeader("appCode", context.getFlowContext().getAppCode()));

            context.pushDefinition(routeDefinition);
            context.writeOutput("from('").writeOutput(uri).writeOutput("').id('"+ id + "')\n");

            return routeDefinition;
        }
    }
}
