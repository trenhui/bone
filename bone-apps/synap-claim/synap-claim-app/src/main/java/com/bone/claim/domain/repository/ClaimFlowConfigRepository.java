package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.config.ClaimFlowConfig;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 租户级流程配置仓储（{@code t_claim_flow_config}，§6.7）。
 *
 * <p><b>「生效版本」的定义</b>：按 {@code (tenant_id, flow_key, config_version)} 定位配置；变更须带 {@code
 * config_version} 发版号（乐观锁整体覆盖）。
 *
 * <p><b>为什么不能直接用 {@code findOneByCriteria}</b>：本表的唯一键是 {@code uk_tenant_flow_version (tenant_id,
 * flow_key, config_version)}——它约束的是<b>版本</b>，不是<b>生效行</b>。同一 {@code (tenant_id, flow_key)}
 * 可以合法地存在多行（不同 {@code config_version}），其中恰好一行 {@code active_flag=1}。 而 SDK 的 {@code
 * findOneByCriteria} 在命中多行时抛 {@code MultipleResultsException}：
 *
 * <pre>{@code
 * // 0099 建表：uk_tenant_flow_version (tenant_id, flow_key, config_version)
 * //            KEY idx_flow (tenant_id, flow_key, active_flag)
 * // 于是 (tenant_id=7, flow_key='ADJ') 可以有 v1(act=0) 与 v2(act=1) 两行
 * findOneByCriteria(eq(tenantId).eq(flowKey))  // ⇒ 命中 2 行 ⇒ MultipleResultsException
 * }</pre>
 *
 * <p>这个缺陷在<b>接线那一刻</b>才暴露（当前无消费方，表也是空的），且报错信息是 {@code Expected single result, found: 2}——完全看不出是「同
 * flow_key 有多个版本」造成的，排查成本极高。故本仓储显式定义生效语义：
 *
 * <ol>
 *   <li>先过滤 {@code active_flag = 1}（历史版本 {@code active_flag = 0} 直接排除）；
 *   <li>再<b>在应用层</b>取 {@code config_version} 最大者——不依赖数据库的返回顺序。
 * </ol>
 *
 * <p><b>为什么排序在应用层而不只靠 SQL</b>：SQL 里也加了 {@code ORDER BY config_version DESC}，但 {@code
 * findByCriteria} 的返回顺序是<b>数据库的实现细节</b>——一旦有人后来改了查询（去掉排序、或走了别的
 * 索引扫描路径），「取第一条就等于取最大版本」这个假设就静默失效，取到的是任意一行。把「取最大」写成应用层的 显式计算，行为就不再依赖任何外部约定；SQL 排序保留只是让数据库少传数据。
 *
 * <p>「多 active」本身是数据缺陷，已由 {@code 0103} 迁移用唯一约束在<b>数据库层</b>堵死（见该脚本）—— 但应用层仍按上述口径取，因为<b>存量环境可能已经存在多
 * active 行</b>，约束只能防新增、不能回溯清理。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimFlowConfigRepository extends Repository<ClaimFlowConfig, Long> {

  /** 生效标记：当前生效版本。 */
  int ACTIVE_FLAG_ON = 1;

  /** 生效标记：历史版本。 */
  int ACTIVE_FLAG_OFF = 0;

  /**
   * 按租户 + 流程键取<b>当前生效</b>的配置。
   *
   * <p>生效语义见类注释：{@code active_flag = 1} 且 {@code config_version} 最大者。无生效行时返回 {@code null}
   * （调用方须按「未配置」处理，不得默认放行）。
   *
   * @param tenantId 租户
   * @param flowKey 流程键
   * @return 生效配置；无生效版本时返回 {@code null}
   */
  default ClaimFlowConfig findByFlowKey(Long tenantId, String flowKey) {
    if (tenantId == null || flowKey == null || flowKey.isBlank()) {
      return null;
    }
    Criteria<ClaimFlowConfig> criteria =
        Criteria.<ClaimFlowConfig>create()
            .eq(ClaimFlowConfig::getTenantId, tenantId)
            .eq(ClaimFlowConfig::getFlowKey, flowKey)
            .eq(ClaimFlowConfig::getActiveFlag, ACTIVE_FLAG_ON)
            .orderByDesc(ClaimFlowConfig::getConfigVersion);
    List<ClaimFlowConfig> rows = findByCriteria(criteria);
    return highestVersion(rows);
  }

  /**
   * 取发版号最大的一行。
   *
   * <p>抽成包级默认方法（而非内联在 {@link #findByFlowKey} 里），是为了让「取最大」这条规则可被单测直接钉住—— 它是本仓储的核心语义，不该只能通过 mock
   * 掉整个仓储来间接覆盖。
   *
   * @param rows 候选行（通常已按 config_version 过滤出 active 行）
   * @return 发版号最大者；无行时返回 {@code null}
   */
  static ClaimFlowConfig highestVersion(List<ClaimFlowConfig> rows) {
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    ClaimFlowConfig best = null;
    for (ClaimFlowConfig row : rows) {
      if (row == null) {
        continue;
      }
      if (best == null) {
        best = row;
        continue;
      }
      Integer candidate = row.getConfigVersion();
      Integer current = best.getConfigVersion();
      // 版本号可空（历史脏数据），可空值不参与「更大」比较——保守取先出现的那行，
      // 避免把 null 当 0 参与比较而选错。
      if (candidate != null && (current == null || candidate > current)) {
        best = row;
      }
    }
    return best;
  }

  /**
   * 按租户 + 流程键 + 发版号取<b>指定版本</b>（含历史版本）。
   *
   * <p>与 {@link #findByFlowKey} 的区别：后者只看生效版本，本方法用于「回看某一版配置」—— 审计复算与问题定位需要能取到历史版本，而历史版本 {@code
   * active_flag = 0}，不按版本查就取不到。
   *
   * <p>该组合由唯一键 {@code uk_tenant_flow_version} 保证唯一，故用 {@code findOneByCriteria} 是安全的。
   *
   * @param tenantId 租户
   * @param flowKey 流程键
   * @param configVersion 发版号
   * @return 该版本配置；不存在时返回 {@code null}
   */
  default ClaimFlowConfig findByVersion(Long tenantId, String flowKey, Integer configVersion) {
    if (tenantId == null || flowKey == null || flowKey.isBlank() || configVersion == null) {
      return null;
    }
    Criteria<ClaimFlowConfig> criteria =
        Criteria.<ClaimFlowConfig>create()
            .eq(ClaimFlowConfig::getTenantId, tenantId)
            .eq(ClaimFlowConfig::getFlowKey, flowKey)
            .eq(ClaimFlowConfig::getConfigVersion, configVersion);
    return findOneByCriteria(criteria);
  }
}
