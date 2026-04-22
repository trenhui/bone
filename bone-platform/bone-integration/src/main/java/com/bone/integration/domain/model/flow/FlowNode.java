package com.bone.integration.domain.model.flow;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.vo.FlowId;
import com.bone.integration.domain.model.flow.vo.FlowNodeId;
import com.bone.integration.domain.model.flow.vo.NodeType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowNode extends AggregateRoot<Long> {
    private FlowNodeId id;
    private FlowId flowId;
    private String name;
    private NodeType type;
    private Map<String, Object> config;
    private int positionX;
    private int positionY;

    public static FlowNode create(FlowId flowId, String name, NodeType type, Map<String, Object> config, int positionX, int positionY) {
        if (flowId == null) {
            throw new DomainException("流程ID不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("节点名称不能为空");
        }
        if (type == null) {
            throw new DomainException("节点类型不能为空");
        }
        if (config == null) {
            throw new DomainException("节点配置不能为空");
        }

        FlowNode node = new FlowNode();
        node.flowId = flowId;
        node.name = name;
        node.type = type;
        node.config = config;
        node.positionX = positionX;
        node.positionY = positionY;
        return node;
    }

    public void update(String name, Map<String, Object> config, int positionX, int positionY) {
        if (name == null || name.isBlank()) {
            throw new DomainException("节点名称不能为空");
        }
        if (config == null) {
            throw new DomainException("节点配置不能为空");
        }
        this.name = name;
        this.config = config;
        this.positionX = positionX;
        this.positionY = positionY;
    }

    void setId(Long id) {
        this.id = FlowNodeId.of(id);
    }
}