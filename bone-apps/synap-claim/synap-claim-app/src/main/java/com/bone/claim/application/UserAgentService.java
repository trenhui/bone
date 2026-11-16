package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.inspection.ClaimUserAgent;
import com.bone.claim.domain.repository.ClaimUserAgentRepository;
import com.bone.core.util.DistributedIdGenerator;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 代理人区间服务（批 4 任务 3.1）。
 *
 * <p>同一被代理人在同一时段至多一条生效代理：写入期事前查重叠（给操作人可辨认的 409），数据库生成列 {@code active_key = IF(state='ACTIVE',
 * principal_id, NULL)} + 唯一键兜底并发双写（恰一条成功）。 区间外不生效由读取侧按 validFrom/validTo 双判断；失效走 {@code deactivate}
 * 改 state 释放唯一名额，不删行、保留审计。
 */
@Service
@RequiredArgsConstructor
public class UserAgentService {

  private final ClaimUserAgentRepository repository;
  private final TenantPort tenantPort;

  /** 代理视图（ID 一律 String）。 */
  public record AgentView(
      String id,
      String principalId,
      String agentId,
      boolean active,
      String validFrom,
      String validTo) {}

  /**
   * 登记代理区间。
   *
   * @param principalId 被代理人（请假/缺席方）
   * @param agentId 代理人
   * @param validFrom 生效起（含）
   * @param validTo 生效止（不含；null = 长期）
   */
  @Transactional
  public AgentView register(
      String principalId, String agentId, Date validFrom, Date validTo, String reason) {
    long tenantId = tenantPort.currentTenantId();
    // 事前查重：任一现存 ACTIVE 代理区间重叠即拒（并发下仍由唯一键兜底）。
    List<ClaimUserAgent> existing = repository.findByPrincipal(principalId);
    if (existing != null) {
      for (ClaimUserAgent e : existing) {
        if (e != null
            && ClaimUserAgent.STATE_ACTIVE.equals(e.getState())
            && e.overlaps(validFrom, validTo)) {
          throw ClaimErrors.of(
              ClaimErrorCodes.SC_CLAIM_AGENT_CONFLICT, principalId + " 在该时段已有生效代理");
        }
      }
    }
    Date now = new Date();
    ClaimUserAgent agent;
    try {
      agent =
          ClaimUserAgent.register(
              DistributedIdGenerator.generateLongId(),
              tenantId,
              principalId,
              agentId,
              validFrom,
              validTo,
              reason,
              now);
    } catch (IllegalArgumentException ex) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_AGENT_CONFLICT, ex.getMessage());
    }
    repository.insert(agent);
    return toView(agent);
  }

  /** 令被代理人当前生效代理失效（改 state，不删行）。 */
  @Transactional
  public void deactivate(String principalId) {
    ClaimUserAgent active = repository.findActiveByPrincipal(principalId);
    if (active == null) {
      return; // 幂等：无生效代理则无需操作
    }
    active.deactivate(new Date());
    repository.update(active);
  }

  /** 取某时点对被代理人有效的代理人 ID；无则返回 null（区间外不生效）。 */
  @Transactional(readOnly = true)
  public String effectiveAgent(String principalId, Date at) {
    ClaimUserAgent active = repository.findActiveByPrincipal(principalId);
    if (active != null && active.isEffectiveAt(at)) {
      return active.getAgentId();
    }
    return null;
  }

  private static AgentView toView(ClaimUserAgent a) {
    return new AgentView(
        String.valueOf(a.getId()),
        a.getPrincipalId(),
        a.getAgentId(),
        ClaimUserAgent.STATE_ACTIVE.equals(a.getState()),
        a.getValidFrom() == null ? null : a.getValidFrom().toInstant().toString(),
        a.getValidTo() == null ? null : a.getValidTo().toInstant().toString());
  }
}
