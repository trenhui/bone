package com.bone.integration.flow.visitor.camel.builder;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.GraphNode;

public interface CamelNodeBuilder<T extends GraphNode> {

    default void enter(T node, CamelBuilderContext context) {}

    void process(T node, CamelBuilderContext context);

    default void leave(T node, CamelBuilderContext context) {}
}
