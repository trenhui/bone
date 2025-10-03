package com.bone.integration.flow.node;


import com.bone.integration.flow.visitor.INodeVisitor;

public class EndNode extends GraphNode {

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
