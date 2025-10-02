package com.bone.lowcode.integration.flow.node;

import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NettyHttpNode extends GraphNode {
    private String uri;
    private String protocol;
    private String contextType;
    private String method;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}
