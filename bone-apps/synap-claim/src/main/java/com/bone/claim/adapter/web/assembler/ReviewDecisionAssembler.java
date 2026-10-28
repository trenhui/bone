package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq.Rejection;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import java.util.StringJoiner;

/**
 * 契约决策请求 → 内部决策入参的组装（拒赔五要素拼装 + 受控原因码校验）。
 *
 * <p><b>为什么单独成类而不是控制器私有方法</b>：这两个规则是拒赔合规的硬约束（R-MAN-03 五要素 + §8.3 受控原因码）， 需要被单测直接钉住；塞在私有方法里只能通过「起
 * Spring 上下文 + 伪造 token」间接验证，实际结果就是没人测。
 */
public final class ReviewDecisionAssembler {

  private ReviewDecisionAssembler() {}

  /**
   * 受控拒赔原因码：非拒赔一律置空，拒赔缺失即 400。
   *
   * <p>不放行「用 comment 里的自由文本代替原因码」——差异归因与拒赔率统计按码聚合，自由文本一进来就断。
   */
  public static String requireRejectType(ReviewDecisionReq request) {
    if (request.decision() != Decision.REJECTED) {
      return null;
    }
    if (request.rejectType() == null || request.rejectType().isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REJECT_REASON_REQUIRED, "缺少受控拒赔原因码 rejectType");
    }
    return request.rejectType().trim();
  }

  /**
   * 拒赔意见：结构化五要素优先拼装，缺项回退 {@code comment}。
   *
   * <p>拼装格式固定为 {@code 事实认定：…；条款依据：…；证据位置：…；申诉渠道：…；法定时限：…}，空项跳过（不写「null」占位）。
   */
  public static String composeComment(ReviewDecisionReq request) {
    if (request.decision() != Decision.REJECTED) {
      return request.comment();
    }
    Rejection rejection = request.rejection();
    if (rejection == null) {
      return request.comment();
    }
    StringJoiner joiner = new StringJoiner("；");
    appendIfPresent(joiner, "事实认定", rejection.factFindings());
    appendIfPresent(joiner, "条款依据", rejection.clauseBasis());
    if (rejection.evidenceRefs() != null && !rejection.evidenceRefs().isEmpty()) {
      joiner.add("证据位置：" + String.join(",", rejection.evidenceRefs()));
    }
    appendIfPresent(joiner, "申诉渠道", rejection.appealChannel());
    appendIfPresent(joiner, "法定时限", rejection.legalDeadline());
    String composed = joiner.toString();
    return composed.isBlank() ? request.comment() : composed;
  }

  private static void appendIfPresent(StringJoiner joiner, String label, String value) {
    if (value != null && !value.isBlank()) {
      joiner.add(label + "：" + value.trim());
    }
  }
}
