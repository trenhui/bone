package com.bone.claim.application.port.out;

import java.util.List;

/**
 * 自动通过前置守门端口（出站）。
 *
 * <p>把"部署态就绪度查询"变成自动通过路径上的真闸：默认实现读 {@code AiCapabilityReadinessService}，
 * 因此查询视图与拦截判定同源，不会出现"报表说绿、放行路径却没查闸门"的分叉。 claimId 传给实现方留作审计上下文；部署态闸门本身与具体案件无关，逐案运行期证据仍只在决策轨迹里。
 */
public interface AiAutoApproveGatePort {

  /**
   * 守门结论。
   *
   * @param wouldBlock blocker 非空 ⇒ fail-closed；空 ⇒ 仅表示部署态不阻断，不构成放款许可
   * @param blockers 受控原因码（不拼接自由文本）
   * @param scopeNote 与治理视图同源的口径声明，随 preflight 响应下发
   */
  record Verdict(boolean wouldBlock, List<String> blockers, String scopeNote) {

    public Verdict {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      scopeNote = scopeNote == null ? "" : scopeNote;
    }

    public static Verdict of(List<String> blockers, String scopeNote) {
      List<String> safe = blockers == null ? List.of() : List.copyOf(blockers);
      return new Verdict(!safe.isEmpty(), safe, scopeNote);
    }

    /** 随 SC_CLAIM_AI_GATE_BLOCKED detail 返回的原因码串。 */
    public String detail() {
      return String.join(",", blockers);
    }
  }

  Verdict guard(long claimId);
}
