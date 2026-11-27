package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.response.SignBatchItemResp;
import com.bone.claim.adapter.web.dto.response.SignBatchResp;
import com.bone.claim.adapter.web.dto.response.SignCaseItemResp;
import com.bone.claim.adapter.web.dto.response.SignCaseSummaryResp;
import com.bone.claim.adapter.web.dto.response.SignTrackResp;
import com.bone.claim.application.ClaimSignCaseSummary;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.SignTrackLog;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** 签收响应装配单一真源。 */
@Mapper(componentModel = "spring")
public interface ClaimSignAssembler {
  ClaimSignAssembler INSTANCE = Mappers.getMapper(ClaimSignAssembler.class);

  // 聚合的 id 来自基类 getId()，与 DTO 的 batchId 不同名：不显式映射会静默输出 null。
  @Mapping(target = "batchId", source = "id")
  @Mapping(target = "createdCaseCount", ignore = true)
  @Mapping(target = "skippedCaseCount", ignore = true)
  @Mapping(target = "manualCaseCount", ignore = true)
  @Mapping(target = "caseItems", ignore = true)
  SignBatchResp toResp(ClaimSignBatch batch);

  SignTrackResp toResp(SignTrackLog track);

  default SignBatchResp toResp(ClaimSignBatch batch, ClaimSignCaseSummary summary) {
    SignBatchResp base = toResp(batch);
    return new SignBatchResp(
        base.batchId(),
        base.batchNo(),
        base.source(),
        base.signType(),
        base.signChannel(),
        base.insuranceCompany(),
        base.insuranceSubsidiary(),
        base.insuringAgency(),
        base.emergency(),
        base.signStatus(),
        base.claimCount(),
        base.imageReadyCount(),
        base.expressCompany(),
        base.expressNo(),
        base.sender(),
        base.signTime(),
        base.createdAt(),
        base.signOperator(),
        base.signRemark(),
        summary.createdCount(),
        summary.skippedCount(),
        summary.manualCount(),
        toItems(summary));
  }

  default List<SignBatchItemResp> toItemResps(List<SignBatchItem> items) {
    return items.stream()
        .map(
            item ->
                new SignBatchItemResp(
                    str(item.getId()),
                    item.getClaimNo(),
                    item.getPolicyNo(),
                    item.getInsuredName(),
                    item.getInsuredIdNo(),
                    item.getIdentityType(),
                    item.isImageReady(),
                    item.getCountingResult(),
                    item.getImageCount(),
                    item.getImageUploadCount(),
                    item.getImageUploadTime(),
                    item.getImageUploader(),
                    item.getCaseStatus(),
                    str(
                        item.getCreatedCaseId() == null
                            ? item.getClaimId()
                            : item.getCreatedCaseId())))
        .toList();
  }

  default SignCaseSummaryResp toSummaryResp(ClaimSignCaseSummary summary) {
    return new SignCaseSummaryResp(
        summary.createdCount(), summary.skippedCount(), summary.manualCount(), toItems(summary));
  }

  private List<SignCaseItemResp> toItems(ClaimSignCaseSummary summary) {
    return summary.items().stream()
        .map(
            item ->
                new SignCaseItemResp(
                    str(item.itemId()),
                    item.claimNo(),
                    str(item.caseId()),
                    item.caseStatus(),
                    item.result()))
        .toList();
  }

  private static String str(Long value) {
    return value == null ? null : value.toString();
  }
}
