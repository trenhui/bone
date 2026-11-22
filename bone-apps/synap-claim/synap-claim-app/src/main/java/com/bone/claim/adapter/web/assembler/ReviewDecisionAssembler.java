package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq;
import com.bone.claim.adapter.web.dto.request.ReviewDecisionReq.Rejection;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * 契约决策请求 → 内部决策入参的组装（拒赔五要素拼装 + 受控原因码校验）。
 *
 * <p><b>为什么单独成类而不是控制器私有方法</b>：这两个规则是拒赔合规的硬约束（R-MAN-03 五要素 + §8.3 受控原因码）， 需要被单测直接钉住；塞在私有方法里只能通过「起
 * Spring 上下文 + 伪造 token」间接验证，实际结果就是没人测。
 */
public final class ReviewDecisionAssembler {

  private ReviewDecisionAssembler() {}

  /**
   * 拒赔证据位置锚点格式（可定位到证据集条目）。
   *
   * <p>形如 {@code EV-001} 或 {@code EV-001#p2}（#后为 1-based 影像页码，正本第 2 页）。只认 ASCII 大写 EV + 4 位序号，可选
   * {@code #p<正整数>}。拒绝自由文本、排序号、路径、超长——拒赔文书一旦写入不可定位的"证据位置"，申诉/复核就无法回放原件，是合规硬伤。
   */
  private static final Pattern EVIDENCE_REF = Pattern.compile("EV-\\d{3,6}(#p[1-9]\\d{0,3})?");

  private static final int MAX_EVIDENCE_REFS = 20;
  private static final int MAX_REF_LENGTH = 32;
  private static final String EV_REF_HINT = "EV-001 或 EV-001#p2（#p 为影像页码）";

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
   * 拒赔证据位置锚点校验（写入期硬约束）。
   *
   * <p>拒赔时 evidenceRefs 必填（至少一项），每项须为 {@link #EVIDENCE_REF} 格式；非拒赔一律跳过。校验的是"能否定位原件"，不是"证据是否
   * 真实存在"——后者依赖证据集状态，属应用层语义，不在此处伪装校验。
   *
   * <p>只接单条决策端点：批量端点契约不带 rejection/evidenceRefs，不适用本校验。
   */
  public static void requireEvidenceRefs(ReviewDecisionReq request) {
    if (request.decision() != Decision.REJECTED) {
      return;
    }
    Rejection rejection = request.rejection();
    List<String> refs = rejection == null ? null : rejection.evidenceRefs();
    if (refs == null || refs.isEmpty()) {
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_REJECT_EVIDENCE_REF_INVALID, "拒赔必填证据位置（" + EV_REF_HINT + "）");
    }
    if (refs.size() > MAX_EVIDENCE_REFS) {
      throw ClaimErrors.of(
          ClaimErrorCodes.SC_CLAIM_REJECT_EVIDENCE_REF_INVALID,
          "证据位置最多 " + MAX_EVIDENCE_REFS + " 项");
    }
    for (String raw : refs) {
      String ref = raw == null ? "" : raw.trim();
      if (ref.isEmpty() || ref.length() > MAX_REF_LENGTH || !EVIDENCE_REF.matcher(ref).matches()) {
        throw ClaimErrors.of(
            ClaimErrorCodes.SC_CLAIM_REJECT_EVIDENCE_REF_INVALID,
            "非法证据位置「" + ref + "」，应为 " + EV_REF_HINT);
      }
    }
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
