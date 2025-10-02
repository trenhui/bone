package com.bone.lowcode.integration.flow.visitor.camel.builder;

import com.bone.lowcode.integration.flow.node.GraphNode;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;

public interface CamelNodeBuilder<T extends GraphNode> {

    default void enter(T node, CamelBuilderContext context) {}

    void process(T node, CamelBuilderContext context);

    default void leave(T node, CamelBuilderContext context) {}
}
