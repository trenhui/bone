package com.bone.integration.domain.model.flow;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
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
public class FlowConnection extends TenantAggregateRoot<Long> {
  @Id

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
  @Deleted
  private Boolean deleted = false;

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
