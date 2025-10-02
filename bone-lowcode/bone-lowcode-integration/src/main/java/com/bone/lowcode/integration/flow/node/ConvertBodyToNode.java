package com.bone.lowcode.integration.flow.node;

import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ConvertBodyToNode extends GraphNode {
    private String type;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}
