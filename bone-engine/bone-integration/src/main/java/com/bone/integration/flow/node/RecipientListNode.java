package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class RecipientListNode extends GraphNode {
    /**
     * 逗号分割
     */
    private String expression;
    private Boolean parallelProcessing;
    private Boolean synchronous;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
