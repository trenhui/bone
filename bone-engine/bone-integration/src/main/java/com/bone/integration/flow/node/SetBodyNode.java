package com.bone.integration.flow.node;


import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SetBodyNode extends GraphNode {
    private String body;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
