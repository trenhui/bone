package com.bone.integration.flow.node;


import com.bone.integration.flow.visitor.INodeVisitor;

public class StartNode extends GraphNode {

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}