package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.model.AiParticipation;
import com.bone.procurement.contract.domain.model.ContractReview;
import org.springframework.stereotype.Service;

/** 只读披露服务：把 AI 参与态映射为前端字段；未知态原样返回、文案「无法确认」。 */
@Service
@ConditionalOnContractAiReviewEnabled
public class AiDisclosureService {

  public static final String TEXT_CANNOT_CONFIRM = "无法确认";
  public static final String TEXT_AVAILABLE = "AI 已参与";
  public static final String TEXT_NOT_INVOLVED = "AI 未参与";

  public AiDisclosureView disclose(ContractReview review) {
    AiParticipation participation =
        review.aiParticipation() == null ? AiParticipation.UNKNOWN : review.aiParticipation();
    String text =
        switch (participation) {
          case AVAILABLE -> TEXT_AVAILABLE;
          case UNAVAILABLE -> TEXT_NOT_INVOLVED;
          case UNKNOWN, UNDETERMINED -> TEXT_CANNOT_CONFIRM;
        };
    // 版本快照只透传展示，不参与 canConfirm 判定——它不构成 AI 参与证据。
    return new AiDisclosureView(
        review.reviewId(),
        participation.disclosureCode(),
        text,
        participation.isAiConfirmed(),
        review.modelVersionSnapshot());
  }
}
