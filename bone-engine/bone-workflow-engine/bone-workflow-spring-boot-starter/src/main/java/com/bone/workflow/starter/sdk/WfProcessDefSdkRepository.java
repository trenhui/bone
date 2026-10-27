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
 * 流程定义（{@code wf_process_def}）的 SDK {@code @Sql} 仓储。
 *
 * <p><b>读路径</b>：取最新发布版 / 版本列表 / 按版本取。{@code definition_json} 的 JSON 原文原样返回，反序列化与损坏 fail-loudly
 * 由适配器负责。
 *
 * <p><b>写路径（2026-10-27 设计器 M0 补齐）</b>：{@link #insertPublished} 插入不可变版本快照、{@link #updateEnabled}
 * 上线下线。此前本接口只读，流程定义只能靠迁移脚本落库——业务方改一个审批人就要发版。写路径的设计前提有两条： ①{@code definition_json} 原样存储（不重新序列化，否则丢弃
 * {@code ui} 段）；②版本号由 {@link #findMaxVersionRow} 的水位 +1 分配，并靠唯一键 {@code uk_wf_def_ver} 兜住并发。
 *
 * <p><b>草稿不在本表</b>：见 {@link WfProcessDraftSdkRepository}。本表的每一行都是不可变快照，在途实例按 {@code def_version}
 * 装载，任何「原地改旧版本」的写法都会让历史单据的审批依据漂移。
 *
 * <p><b>租户口径</b>：接口级 {@link TenantScopeMode#MANUAL}，每条 SQL 显式写 {@code tenant_id} 谓词。
 */
@TenantScope(TenantScopeMode.MANUAL)
public interface WfProcessDefSdkRepository extends Repository<WfProcessDefRow, String> {

  /**
   * 取最新发布版：{@code status='PUBLISHED' AND enabled=1} 中 {@code max(def_version)}。
   *
   * <p><b>不得用 updated_at 决胜</b>：改版重发不保证 updated_at 与版本号同序（补丁式修订会改旧行）， 版本号才是权威序。
   */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND status = 'PUBLISHED'"
          + "   AND enabled = 1 AND deleted = 0 ORDER BY def_version DESC LIMIT 1")
  Optional<WfProcessDefRow> findLatestPublishedRow(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /** 全部发布版（升序，供版本列表/审计）；下线版（{@code enabled=0}）跳过。 */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND status = 'PUBLISHED'"
          + "   AND enabled = 1 AND deleted = 0 ORDER BY def_version")
  List<WfProcessDefRow> listPublishedRows(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /**
   * 按版本取（在途实例钉启动时的版本快照，不随改版漂移）。
   *
   * <p>2026-10-27 补 {@code status='PUBLISHED'}：此前只判 {@code deleted=0}，与 {@code
   * findLatestPublishedRow} 的谓词不对称。草稿独立成表后 {@code wf_process_def} 理论上只会有 PUBLISHED 行，但把
   * 状态谓词写全，可以让「将来有人往这张表塞 DRAFT 行」时读侧不会读出两行而抛 {@code
   * IncorrectResultSizeDataAccessException}——那个异常会在在途实例推进时出现，与根因完全脱节。
   */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json, enabled FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND def_version = #{version} AND tenant_id = #{tenantId}"
          + "   AND status = 'PUBLISHED' AND deleted = 0")
  Optional<WfProcessDefRow> findByVersionRow(
      @Param("defKey") String defKey,
      @Param("version") int version,
      @Param("tenantId") Long tenantId);

  // ====================== 写路径（设计器发布，2026-10-27） ======================

  /**
   * 该 key 的版本水位：只看 {@code def_version} 最大值，<b>不过滤 {@code enabled}／{@code status}</b>。
   *
   * <p><b>为什么不能复用 {@link #listPublishedRows}</b>：它带 {@code enabled = 1}，一旦某版本被下线，最大值会回落，
   * 新版本号就会与已下线版本撞号 → 撞唯一键 {@code uk_wf_def_ver} → 发布永久失败且报错信息与「发布」无关。 版本号是<b>单调占位</b>，与启用状态无关。
   */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND deleted = 0"
          + " ORDER BY def_version DESC LIMIT 1")
  Optional<WfProcessDefRow> findMaxVersionRow(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /**
   * 发布：插入一条不可变版本快照。
   *
   * <p>{@code definition_json} 必须是<b>调用方提交的原文</b>（含引擎不读的 {@code ui} 段）。若先反序列化成 {@code
   * WorkflowDefinition} 再回写，{@code ui} 会被静默丢弃，表现为「每次发布后流程图版式归零」，且日志里没有任何异常。
   */
  @Sql(
      "INSERT INTO wf_process_def (id, tenant_id, def_key, def_name, def_version, status, enabled,"
          + " definition_json, created_by, created_at, updated_by, updated_at, deleted, version)"
          + " VALUES (#{id}, #{tenantId}, #{defKey}, #{defName}, #{defVersion}, 'PUBLISHED', 1,"
          + " #{definitionJson}, #{operatorId}, #{now}, #{operatorId}, #{now}, 0, 0)")
  int insertPublished(
      @Param("id") String id,
      @Param("tenantId") Long tenantId,
      @Param("defKey") String defKey,
      @Param("defName") String defName,
      @Param("defVersion") int defVersion,
      @Param("definitionJson") String definitionJson,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  /**
   * 下线 / 上线某版本：改 {@code enabled}，<b>不改 {@code status}，不删行</b>。
   *
   * <p>版本快照必须保留：在途实例仍钉着这一版的定义推进，删行会让「审批依据」凭空消失。
   */
  @Sql(
      "UPDATE wf_process_def SET enabled = #{enabled}, updated_by = #{operatorId},"
          + " updated_at = #{now}, version = version + 1"
          + " WHERE tenant_id = #{tenantId} AND def_key = #{defKey} AND def_version = #{defVersion}"
          + "   AND deleted = 0")
  int updateEnabled(
      @Param("tenantId") Long tenantId,
      @Param("defKey") String defKey,
      @Param("defVersion") int defVersion,
      @Param("enabled") int enabled,
      @Param("operatorId") String operatorId,
      @Param("now") Instant now);

  /** 某 key 的全部版本（含已下线，供版本列表与审计；{@code enabled} 一并取出）。 */
  @Sql(
      "SELECT def_key, def_name, def_version, definition_json, enabled FROM wf_process_def"
          + " WHERE def_key = #{defKey} AND tenant_id = #{tenantId} AND status = 'PUBLISHED'"
          + "   AND deleted = 0 ORDER BY def_version")
  List<WfProcessDefRow> listVersionRows(
      @Param("defKey") String defKey, @Param("tenantId") Long tenantId);

  /**
   * 定义总览：每个 key 只取「已发布且启用」的最高版本。
   *
   * <p>用相关子查询而不是 {@code GROUP BY}：MySQL 与 H2 对「非聚合列 + GROUP BY」的容忍度不同（{@code
   * ONLY_FULL_GROUP_BY}），子查询在两处语义一致，且 H2 回归跑的就是生产 SQL。
   */
  @Sql(
      "SELECT d.def_key, d.def_name, d.def_version, d.definition_json, d.enabled FROM wf_process_def d"
          + " WHERE d.tenant_id = #{tenantId} AND d.deleted = 0 AND d.status = 'PUBLISHED'"
          + "   AND d.enabled = 1"
          + "   AND d.def_version = (SELECT MAX(x.def_version) FROM wf_process_def x"
          + "        WHERE x.tenant_id = d.tenant_id AND x.def_key = d.def_key"
          + "          AND x.deleted = 0 AND x.status = 'PUBLISHED' AND x.enabled = 1)"
          + " ORDER BY d.def_key")
  List<WfProcessDefRow> listLatestPerKeyRows(@Param("tenantId") Long tenantId);
}
