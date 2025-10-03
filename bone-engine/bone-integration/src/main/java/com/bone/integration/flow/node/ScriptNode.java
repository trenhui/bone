package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ScriptNode extends GraphNode {
    private String language;
    private String expression;



    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
