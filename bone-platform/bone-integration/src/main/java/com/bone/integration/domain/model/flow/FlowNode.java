package com.bone.integration.domain.model.flow;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.valueobject.NodeType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("int_flow_node")
public class FlowNode extends TenantAggregateRoot<Long> {

  /**
   * 逻辑删除标记。
   *
   * <p><b>为何必须显式声明</b>：bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是 <b>实体内是否存在带
   * {@link com.bone.core.annotation.Deleted} 的字段</b>，与 DDL 有无 {@code deleted} 列无关。 {@code
   * TenantAggregateRoot} 只提供 {@code tenantId}，不像 {@code AbstractEntity} 那样自带该字段； 本类若不声明，{@code
   * Repository#deleteById} 会发出 {@code DELETE FROM} —— 删除即整行物理消失、不可审计不可恢复。
   *
   * <p>本表<b>无唯一索引</b>，故恢复软删不存在「同值无法重建」冲突（见soft-delete-declaration-baseline.json 的 {@code
   * _uk_conflict} 段）。
   */
  @Deleted private Boolean deleted = false;

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
