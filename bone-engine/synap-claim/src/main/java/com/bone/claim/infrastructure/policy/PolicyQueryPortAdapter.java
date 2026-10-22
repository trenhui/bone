package com.bone.claim.infrastructure.policy;

import com.bone.claim.application.port.out.PolicyQueryPort;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 保单快照查询适配器（出站端口实现，必须 {@code *PortAdapter} 命名）。
 *
 * <p><b>一期为进程内存桩</b>：保单/承保系统（§3.5）未接入前，以存量同步的保单快照应答报案前置校验； 二期替换为真实保单系统 RPC/REST
 * 客户端——替换只动本类，领域与应用层零感知（防腐层 ACL 价值即在此）。键 = {@code policyNo + "|" + planId}。
 */
@Component
public class PolicyQueryPortAdapter implements PolicyQueryPort {

  private final Map<String, PolicySnapshot> snapshots = new ConcurrentHashMap<>();

  /** upsert 保单快照（存量同步入口，后续由 schedule/消息驱动）。 */
  public void upsert(PolicySnapshot snapshot) {
    snapshots.put(key(snapshot.policyNo(), snapshot.planId()), snapshot);
  }

  @Override
  public Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId) {
    PolicySnapshot snapshot = snapshots.get(key(policyNo, planId));
    if (snapshot == null) {
      return Optional.empty();
    }
    Instant now = Instant.now();
    boolean active =
        !now.isBefore(snapshot.effectiveFrom()) && !now.isAfter(snapshot.effectiveTo());
    return active ? Optional.of(snapshot) : Optional.empty();
  }

  private static String key(String policyNo, Long planId) {
    return policyNo + "|" + planId;
  }
}
