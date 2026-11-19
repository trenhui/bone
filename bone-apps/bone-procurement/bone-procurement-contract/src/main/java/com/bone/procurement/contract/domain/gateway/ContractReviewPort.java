package com.bone.procurement.contract.domain.gateway;

import com.bone.procurement.contract.domain.model.ReviewRequest;
import com.bone.procurement.contract.domain.model.ReviewSuggestion;

/** 合同审查出站端口：委托 bone-ai-gateway 给出建议；AI 仅辅助，最终决策归人工。 */
public interface ContractReviewPort {

  ReviewSuggestion review(ReviewRequest request);
}
