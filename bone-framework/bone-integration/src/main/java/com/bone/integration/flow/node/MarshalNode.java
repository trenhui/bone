package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MarshalNode extends GraphNode {
    private String type;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}
