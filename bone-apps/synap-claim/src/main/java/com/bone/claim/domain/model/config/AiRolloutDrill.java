package com.bone.claim.domain.model.config;

import java.util.List;
import java.util.Objects;

/**
 * AI 放量回滚演练（只读、纯领域、零 IO）。
 *
 * <p>依据 Google《ML Test Score》「dry-run rollbacks and verify state integrity」与 NIST AI RMF
 * 的应急处置要求：放量必须可回退，且回退演练不得改变任何生效状态。本对象只做推演——不持有写端口、 不读配置库、不触发降档；报告里的 mutations 恒为 0
 * 是结构性事实（无写能力），不是测量值。 「演练前后状态不变」由前后端 E2E 在运行期验证。
 *
 * <p>回退语义复用 {@link AiRolloutStage#previous()}：默认只降一档，避免一次抖动把业务打停； 已在最低档 SHADOW
 * 时目标为自身（atFloor=true），仍是合法且安全的演练结果。
 */
public final class AiRolloutDrill {

  /** 演练禁止触碰的生效状态（与「AI 不碰钱」及放量纪律一致）。 */
  public static final List<String> FORBIDDEN_MUTATIONS =
      List.of("资金与支付", "自动通过阈值", "放量开关与生效档位", "拒赔结论", "冻结集与盲标样本");

  private AiRolloutDrill() {}

  public record Report(
      AiRolloutStage currentStage,
      AiRolloutStage rollbackTargetStage,
      boolean atFloor,
      boolean autoSettlementAfterRollback,
      boolean modelParticipationRequiredAfterRollback,
      boolean integrityIntact,
      int mutations,
      List<String> forbiddenMutations,
      String basis) {
    public Report {
      forbiddenMutations = List.copyOf(forbiddenMutations);
    }
  }

  public static Report of(AiRolloutStage current) {
    Objects.requireNonNull(current, "current 不可为 null");
    AiRolloutStage target = current.previous();
    boolean atFloor = target == current;
    boolean neverEscalates = target.rank() <= current.rank();
    boolean neverWidensAutoSettlement =
        !target.permitsAutoSettlement() || current.permitsAutoSettlement();
    boolean integrityIntact = neverEscalates && neverWidensAutoSettlement;
    String basis =
        atFloor
            ? "已在最低档 SHADOW：回退目标为自身，演练确认无可再降空间且不改变生效状态"
            : "回退一档至 " + target.name() + "；资金权限不高于当前档，模型参与要求按目标档重算";
    return new Report(
        current,
        target,
        atFloor,
        target.permitsAutoSettlement(),
        target.requiresModelParticipation(),
        integrityIntact,
        0,
        FORBIDDEN_MUTATIONS,
        basis);
  }
}
