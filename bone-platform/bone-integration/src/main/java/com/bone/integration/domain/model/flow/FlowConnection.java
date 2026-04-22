package com.bone.integration.domain.model.flow;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.integration.domain.model.flow.vo.FlowNodeId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowConnection extends AggregateRoot<Long> {
    private Long id;
    private FlowId flowId;
    private FlowNodeId sourceNodeId;
    private FlowNodeId targetNodeId;
    private String condition;

    public static FlowConnection create(FlowId flowId, FlowNodeId sourceNodeId, FlowNodeId targetNodeId, String condition) {
        if (flowId == null) {
            throw new DomainException("流程ID不能为空");
        }
        if (sourceNodeId == null) {
            throw new DomainException("源节点ID不能为空");
        }
        if (targetNodeId == null) {
            throw new DomainException("目标节点ID不能为空");
        }

        FlowConnection connection = new FlowConnection();
        connection.flowId = flowId;
        connection.sourceNodeId = sourceNodeId;
        connection.targetNodeId = targetNodeId;
        connection.condition = condition;
        return connection;
    }

    public void update(String condition) {
        this.condition = condition;
    }

    void setId(Long id) {
        this.id = id;
    }
}