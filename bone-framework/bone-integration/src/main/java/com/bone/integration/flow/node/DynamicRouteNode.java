package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DynamicRouteNode extends GraphNode {
    private String dynamicRouteId;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}
