package com.bone.workflow.starter.sdk;

import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 流程实例（{@code wf_instance}）的 SDK {@code @Sql} 仓储。
 *
 * <p>SQL 与并发口径随方法文档沉淀在此（2026-10-24 自裸 JDBC 收敛，HC-006 豁免随之移除）；H2 测试 （{@code
 * wf-schema-h2.sql}）原样执行这些语句，避免「测试另写一份 SQL → 测试绿、生产死锁」。 审批任务（{@code wf_task}）的语句在 {@link
 * WfTaskSdkRepository}——按表一个接口，与行类一一对应。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词—— SDK 的
 * {@code @Sql} 通道不经过租户过滤器，漏谓词即多租户串数据，且单租户单测完全看不出来。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfInstanceSdkRepository extends Repository<WfInstanceRow, String> {

  /**
   * 新建实例行。
   *
   * <p>{@code created_at} 用 {@code now}、{@code updated_at} 用调用方传入的 {@code updatedAt}（与领域实例的 {@code
   * updatedAt} 对齐）；审计列 {@code created_by}/{@code updated_by} 均由 {@code operatorId} 显式提供
   * （端口纪律：审计列回答「谁」，不得用租户 id 冒充）。
   */
  @Sql(
      "INSERT INTO wf_instance (id, tenant_id, entity_code, record_id, def_key, def_version,"
          + " current_node_code, phase, started_by, started_at, node_round, node_entered_at,"
          + " reject_rounds, updated_at, created_by, created_at,"
          + " updated_by, deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{entityCode}, #{recordId}, #{defKey}, #{defVersion},"
          + " #{currentNodeCode}, #{phase}, #{startedBy}, #{startedAt}, 0, #{startedAt}, 0,"
          + " #{updatedAt}, #{operatorId}, #{now}, #{operatorId}, 0, 0)")
  int insertInstance(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("defKey") String defKey,
      @Param("defVersion") int defVersion,
      @Param("currentNodeCode") String currentNodeCode,
      @Param("phase") String phase,
      @Param("startedBy") String startedBy,
      @Param("startedAt") Instant startedAt,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  /**
   * 更新实例行（阶段 / 元信息落库）；{@code version = version + 1} 供乐观锁检测同行丢失更新。
   *
   * <p><b>刻意不写 {@code current_node_code}</b>：节点只能由 {@link #compareAndAdvanceNode} 原子推进改写。本方法被「撤回 /
   * 驳回 / 挂起 / 恢复 / 终止」等路径调用，它们传入的多是<b>推进前读到的旧快照</b>； 若把 {@code currentNodeCode} 一并写回，一次基于旧快照的
   * {@code saveInstance} 就能把已推进的节点<b>回退</b>， 而实例版本号照常 +1、phase 也正确——事后从数据上完全看不出曾经回退过。
   *
   * <p>{@code currentNodeCode} 参数保留（INSERT 分支仍需要），但 UPDATE 分支不再使用。
   */
  @Sql(
      "UPDATE wf_instance SET entity_code = #{entityCode}, record_id = #{recordId}, def_key = #{defKey},"
          + " def_version = #{defVersion}, phase = #{phase},"
          + " started_by = #{startedBy}, started_at = #{startedAt}, updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId}")
  int updateInstance(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("defKey") String defKey,
      @Param("defVersion") int defVersion,
      @Param("currentNodeCode") String currentNodeCode,
      @Param("phase") String phase,
      @Param("startedBy") String startedBy,
      @Param("startedAt") Instant startedAt,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  @Sql(
      "SELECT id, entity_code, record_id, def_key, def_version, current_node_code, phase, started_by,"
          + " started_at, updated_at FROM wf_instance"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  Optional<WfInstanceRow> findInstanceRow(@Param("id") String id, @Param("tenantId") Long tenantId);

  /**
   * 按业务单据取<b>最新</b>实例（驳回/撤回后重提是新实例，历史按单据可累积）。
   *
   * <p>排序按<b>时间列</b>（{@code started_at DESC, id DESC}）：id 在 UUID 宿主下字典序≠时间序，按 id 决胜会取错实例。
   */
  @Sql(
      "SELECT id, entity_code, record_id, def_key, def_version, current_node_code, phase, started_by,"
          + " started_at, updated_at FROM wf_instance"
          + " WHERE entity_code = #{entityCode} AND record_id = #{recordId} AND tenant_id = #{tenantId}"
          + " AND deleted = 0 ORDER BY started_at DESC, id DESC LIMIT 1")
  Optional<WfInstanceRow> findLatestInstanceRowByRecord(
      @Param("entityCode") String entityCode,
      @Param("recordId") String recordId,
      @Param("tenantId") Long tenantId);

  /**
   * 按阶段列举实例（问题单据入口：{@code SUSPENDED} 死单、{@code REJECTED} 驳回单），时间倒序 + {@code LIMIT} 截断。
   *
   * <p><b>只给 limit 不给 count</b>：问题单据是运维排障入口，不是分页浏览——超限直接截断并由上层提示「不止这些」， 比为一张排障页维护 count
   * 口径（相位还会随处置实时变化，count 与列表两次查询天然不一致）更诚实。
   */
  @Sql(
      "SELECT id, entity_code, record_id, def_key, def_version, current_node_code, phase, started_by,"
          + " started_at, updated_at FROM wf_instance"
          + " WHERE tenant_id = #{tenantId} AND deleted = 0 AND phase = #{phase}"
          + " ORDER BY updated_at DESC, id DESC LIMIT #{limit}")
  List<WfInstanceRow> listInstanceRowsByPhase(
      @Param("tenantId") Long tenantId, @Param("phase") String phase, @Param("limit") int limit);

  /**
   * 实例相位 CAS：仅当当前 phase 仍为 {@code expected} 时才改。
   *
   * <p><b>只 guard phase，不 guard version</b>：端口签名无 expectedVersion；version 列 +1 仅作同行丢失更新检测， 不作第二
   * guard（设计文档 §4.2 决策 3）。终态 phase CAS 由调用方（适配器）同事务释放活动指针。
   */
  @Sql(
      "UPDATE wf_instance SET phase = #{next}, updated_at = #{updatedAt}, updated_by = #{operatorId},"
          + " version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND phase = #{expected}")
  int compareAndSetPhase(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("expected") String expected,
      @Param("next") String next,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /**
   * 节点级原子推进（会签场景的关键并发点）：完成条件的判定与推进在同一语句内依据最新行状态计算。
   *
   * <p>完成条件口径（{@code mode}）：{@code AUTO} 无条件移动（START / CONDITION / CC 等非审批节点）； {@code ALL} 会签 =
   * 全部任务落在已决策显式集（{@code APPROVED/REJECTED/CANCELLED}，不用「无 PENDING」近似） 且无人驳回；{@code ANY} 或签 = 至少 1 人
   * APPROVED 且无人驳回；{@code SINGLE} 单签 = 至少 1 人 APPROVED（驳回由 completeTask 兜底整单终止， 此处不校验
   * REJECTED，否则「驳回退回重入」时历史访问的陈旧 REJECTED 会误阻断本次推进）；{@code RATIO} 比例会签 = 已通过人数 ≥ {@code
   * quorum}（容忍单条驳回，只要法定人数仍可达，由引擎兜底「不可达则整单驳回」）。
   *
   * <p><b>REJECTED 守卫的适用范围</b>：仅 {@code ALL}/{@code ANY} 多办理人同访问需要——任一驳回即整单驳回； {@code
   * SINGLE}/{@code RATIO}/{@code AUTO} 不挂此守卫（单签驳回由引擎兜底；比例会签容忍驳回；非审批节点本就无驳回）。
   *
   * <p><b>为什么不得拆成先查后改</b>：两个会签人同时点「同意」时各自读到「对方还没通过」的旧快照 ⇒ 双方都判「未齐」 ⇒
   * 流程永远卡在待办（会签经典死锁）。判据必须由数据库在同一条语句里算。
   *
   * <p><b>轮次过滤（{@code node_round}）</b>：所有任务子查询都带 {@code t.node_round = wf_instance.node_round}，推进时
   * {@code node_round} 自增。这是驳回退回（同一节点出现<b>多批</b>任务）后判据不被历史污染的唯一办法： 否则上一轮遗留的 {@code APPROVED}
   * 会让「本轮只要一人同意」即凑满 {@code quorum}——比例会签可被直接绕过。 用整数轮次而非 {@code created_at >= node_entered_at}
   * 比较时间戳：任务与实例更新可能落在同一毫秒（H2 固定时钟下单测必然如此）， 时间戳比较会退化，整数轮次不会。
   */
  @Sql(
      "UPDATE wf_instance SET current_node_code = #{nextNodeCode},"
          + " node_round = node_round + 1, node_entered_at = #{updatedAt},"
          + " updated_at = #{updatedAt}, updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{instanceId} AND tenant_id = #{tenantId}"
          + "   AND current_node_code = #{nodeCode}"
          + "   AND phase = 'RUNNING'"
          + "   AND (#{mode} NOT IN ('ALL', 'ANY')"
          + "     OR NOT EXISTS (SELECT 1 FROM wf_task t"
          + "         WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "           AND t.node_code = #{nodeCode}"
          + "           AND t.node_round = wf_instance.node_round"
          + "           AND t.deleted = 0 AND t.status = 'REJECTED'))"
          + "   AND ("
          + "     #{mode} = 'AUTO'"
          + "     OR (#{mode} = 'ALL'"
          + "       AND EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = #{nodeCode}"
          + "             AND t.node_round = wf_instance.node_round"
          + "             AND t.deleted = 0)"
          + "       AND NOT EXISTS (SELECT 1 FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = #{nodeCode}"
          + "             AND t.node_round = wf_instance.node_round"
          + "             AND t.deleted = 0"
          + "             AND t.status NOT IN ('APPROVED', 'REJECTED', 'CANCELLED')))"
          + "     OR (#{mode} = 'RATIO'"
          + "       AND (SELECT COUNT(*) FROM wf_task t"
          + "           WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "             AND t.node_code = #{nodeCode}"
          + "             AND t.node_round = wf_instance.node_round"
          + "             AND t.deleted = 0 AND t.status = 'APPROVED')"
          + "         >= #{quorum})"
          + "     OR (#{mode} NOT IN ('ALL', 'RATIO') AND EXISTS (SELECT 1 FROM wf_task t"
          + "         WHERE t.instance_id = wf_instance.id AND t.tenant_id = wf_instance.tenant_id"
          + "           AND t.node_code = #{nodeCode}"
          + "           AND t.node_round = wf_instance.node_round"
          + "           AND t.deleted = 0 AND t.status = 'APPROVED'))"
          + "   )")
  int compareAndAdvanceNode(
      @Param("instanceId") String instanceId,
      @Param("tenantId") Long tenantId,
      @Param("nodeCode") String nodeCode,
      @Param("nextNodeCode") String nextNodeCode,
      @Param("mode") String mode,
      @Param("quorum") Integer quorum,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /**
   * 累加一次「驳回退回」，返回累加后的累计次数（循环防护计数）。
   *
   * <p>累加必须落在实例行上（{@code reject_rounds}），不能由应用层「数 REJECTED 任务条数」推导：一个会签节点上 3 人驳回 会留下 3 条 {@code
   * REJECTED}，按条数计就把<b>一轮往返数成三轮</b>，上限提前打满、单据被误终止。
   */
  @Sql(
      "UPDATE wf_instance SET reject_rounds = reject_rounds + 1, updated_at = #{updatedAt},"
          + " updated_by = #{operatorId}, version = version + 1"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  int incrementRejectRounds(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("updatedAt") Instant updatedAt,
      @Param("operatorId") String operatorId);

  /** 读累计驳回退回次数（{@link #incrementRejectRounds} 之后同事务读取，保证读到的是自增后的值）。 */
  @Sql(
      "SELECT reject_rounds FROM wf_instance"
          + " WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = 0")
  Integer findRejectRounds(@Param("id") String id, @Param("tenantId") Long tenantId);

  /**
   * 卡死实例检测（看门狗）：RUNNING 且当前节点停留超过 cutoff、且无任何 PENDING 任务的实例 id。
   *
   * <p><b>跨租户</b>：与 {@code pollDueStatusIntents} / {@code findTerminalMissingProjection}
   * 同口径——看门狗跑在无 TenantContext 的调度线程， 租户由行内 {@code tenant_id}
   * 携带；此处刻意<b>不</b>按单租户过滤（漏上下文线程要么抛错要么只扫一个租户）。仅做只读检测，不触达业务动作。
   *
   * <p>判据排除「有 PENDING 任务」的实例——那是人迟迟不审的正常等待，不是死实例。
   */
  @Sql(
      "SELECT i.id FROM wf_instance i"
          + " WHERE i.deleted = 0 AND i.phase = 'RUNNING'"
          + "   AND i.node_entered_at <= #{cutoff}"
          + "   AND NOT EXISTS (SELECT 1 FROM wf_task t"
          + "       WHERE t.instance_id = i.id AND t.tenant_id = i.tenant_id"
          + "         AND t.deleted = 0 AND t.status = 'PENDING')")
  List<String> findStuckInstanceIds(@Param("cutoff") Instant cutoff);
}
