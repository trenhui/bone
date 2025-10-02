package com.bone.lowcode.integration.flow.node;

import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TransformNode extends GraphNode {
    private String content;
    private String filePath;
    private String inputFormat;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this);
    }
}
