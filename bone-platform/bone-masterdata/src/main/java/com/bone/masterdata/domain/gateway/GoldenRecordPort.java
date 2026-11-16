package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.goldenrecord.GoldenRecord;
import com.bone.masterdata.domain.model.goldenrecord.MergeAudit;
import java.util.Optional;

/**
 * Golden Record 快照与合并审计的出站端口（平台侧唯一 system of entry 的持久化边界）。
 *
 * <p><b>终局契约</b>：本接口是六边形架构的出站端口，语义不随 adapter 变化。生产实现由 L3 的 JDBC adapter （{@code mdm_golden_record}
 * / {@code mdm_merge_audit} 类型化字段表）提供；届时编排层与领域内核零改动， 不存在「先域内自建、二期上移权威」的割接。
 *
 * <p><b>原子性要求</b>：{@link #appendSnapshot} 必须在同一事务内同时落快照与审计。只落快照不落审计会让误合并
 * 无法整笔回滚；只落审计不落快照会让消费侧读到不存在的版本。
 */
public interface GoldenRecordPort {

  /** 取某租户下某统一身份的当前生效快照；未建立返回 empty。 */
  Optional<GoldenRecord> findCurrent(long tenantId, String partyId);

  /** 按合并号取审计（含 before/after 完整快照与逐字段裁决）；用于回滚与事后仲裁。 */
  Optional<MergeAudit> findAudit(long tenantId, String mergeId);

  /**
   * 追加一个版本快照及其合并审计（同事务）。
   *
   * <p>回滚复用本方法：{@code snapshot} 传恢复后的合并前快照，{@code audit} 传已标记撤销的原审计； adapter 按 {@code mergeId}
   * 覆盖审计状态，不得追加第二条审计造成重复回滚失效。
   */
  void appendSnapshot(GoldenRecord snapshot, MergeAudit audit);

  /**
   * 撤销到「无」（首次建记录被回滚）。
   *
   * <p><b>必须与 {@code tombstone} 的撤销标记在同一事务持久化</b>：否则审计仍为未撤销态，同一 mergeId 可被
   * 重复回滚并重复投递补偿事件。终局实现需保留墓碑版本，不得物理删除审计。
   */
  void clearCurrent(long tenantId, String partyId, MergeAudit tombstone);
}
