package com.bone.integration.domain.flow;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_flow_connection")
public class FlowConnection extends AggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long flowId;
  private Long sourceNodeId;
  private Long targetNodeId;
  private String condition;

  public static FlowConnection create(
      Long id, Long flowId, Long sourceNodeId, Long targetNodeId, String condition) {
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
    connection.id = id;
    connection.flowId = flowId;
    connection.sourceNodeId = sourceNodeId;
    connection.targetNodeId = targetNodeId;
    connection.condition = condition;
    return connection;
  }

  public void update(String condition) {
    this.condition = condition;
  }
}
