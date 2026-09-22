package com.bone.integration.domain.flow;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.vo.NodeType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_flow_node")
public class FlowNode extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long flowId;
  private String name;
  private NodeType type;
  private Map<String, Object> config;
  private int positionX;
  private int positionY;

  public static FlowNode create(
      Long id,
      Long flowId,
      String name,
      NodeType type,
      Map<String, Object> config,
      int positionX,
      int positionY) {
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
    node.id = id;
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
}
