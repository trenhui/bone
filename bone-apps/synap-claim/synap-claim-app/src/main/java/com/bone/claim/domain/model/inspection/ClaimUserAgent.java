package com.bone.claim.domain.model.inspection;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;

/**
 * 代理区间（append-only 事实，不可改写）。
 *
 * <p>state 由应用层在登记/失效时维护，是生成列 active_key 的唯一性口径——MySQL 生成列禁止 NOW()， 不能直接判"区间生效"。区间外不生效由查询侧按
 * validFrom/validTo 双判断。 active_key 是生成列，不进实体（SDK 按实体元数据生成 INSERT 列清单，写进去报未知列）。
 */
@Getter
@Table("t_claim_user_agent")
public class ClaimUserAgent extends TenantAbstractEntity<Long> {

  public static final String STATE_ACTIVE = "ACTIVE";
  public static final String STATE_INACTIVE = "INACTIVE";

  @Column(name = "principal_id", nullable = false)
  private String principalId;

  @Column(name = "agent_id", nullable = false)
  private String agentId;

  @Column(name = "valid_from", nullable = false)
  private Date validFrom;

  @Column(name = "valid_to")
  private Date validTo;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "reason")
  private String reason;

  ClaimUserAgent() {}

  public static ClaimUserAgent register(
      Long id,
      Long tenantId,
      String principalId,
      String agentId,
      Date validFrom,
      Date validTo,
      String reason,
      Date now) {
    if (principalId == null || principalId.isBlank()) {
      throw new IllegalArgumentException("被代理人不能为空");
    }
    if (agentId == null || agentId.isBlank()) {
      throw new IllegalArgumentException("代理人不能为空");
    }
    if (validFrom == null || (validTo != null && !validTo.after(validFrom))) {
      throw new IllegalArgumentException("代理区间非法：生效止必须晚于生效起");
    }
    ClaimUserAgent e = new ClaimUserAgent();
    e.setId(id);
    e.setTenantId(tenantId);
    e.principalId = principalId;
    e.agentId = agentId;
    e.validFrom = validFrom;
    e.validTo = validTo;
    e.state = STATE_ACTIVE;
    e.reason = reason == null ? "" : reason;
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    return e;
  }

  /** 区间外不生效（边界：validFrom 含、validTo 不含）。 */
  public boolean isEffectiveAt(Date at) {
    if (!STATE_ACTIVE.equals(state)) {
      return false;
    }
    if (at.before(validFrom)) {
      return false;
    }
    return validTo == null || at.before(validTo);
  }

  /** 令代理失效（改 state 以释放生成列唯一名额，不删行、保审计）。 */
  public void deactivate(Date now) {
    this.state = STATE_INACTIVE;
    this.setUpdatedAt(now);
  }

  /** 区间是否重叠（左闭右开；用于写入期事前查重）。 */
  public boolean overlaps(Date from, Date to) {
    if (validTo != null && !validTo.after(from)) {
      return false;
    }
    if (to != null && !to.after(validFrom)) {
      return false;
    }
    return true;
  }
}
