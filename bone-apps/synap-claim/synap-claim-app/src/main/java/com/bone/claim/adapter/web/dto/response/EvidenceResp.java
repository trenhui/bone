package com.bone.claim.adapter.web.dto.response;

import com.bone.claim.domain.model.evidence.Evidence;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 证据材料列表项（只读响应契约）。
 *
 * <p>材料列表原先直接返回 {@code Evidence} 聚合根，把租户字段、乐观锁版本与 SDK 映射注解的序列化形态一并暴露；
 * 聚合根加一列就会无意识改掉对外契约（HC-003）。这里固定字段，前端解析端点所需的 {@code id} 保留。
 */
@Schema(description = "证据材料（只读）")
public record EvidenceResp(
    @Schema(description = "材料ID") Long id,
    @Schema(description = "案件ID") Long claimId,
    @Schema(description = "材料类型") String docType,
    @Schema(description = "附件引用（平台附件服务）") String fileRef,
    @Schema(description = "文件内容 SHA256") String contentHash,
    @Schema(description = "解析状态") String ocrStatus,
    @Schema(description = "解析置信度") BigDecimal ocrConfidence,
    @Schema(description = "票面验真结果") String verifyValid,
    @Schema(description = "解析结果 JSON") String extractedJson,
    @Schema(description = "是否锁定") Integer locked,
    @Schema(description = "锁定时间") Instant lockedAt,
    @Schema(description = "创建时间") Instant createdAt,
    @Schema(description = "更新时间") Instant updatedAt) {

  public static EvidenceResp of(Evidence evidence) {
    return new EvidenceResp(
        ((Number) evidence.getId()).longValue(),
        evidence.getClaimId(),
        evidence.getDocType(),
        evidence.getFileRef(),
        evidence.getContentHash(),
        evidence.getOcrStatus(),
        evidence.getOcrConfidence(),
        evidence.getVerifyValid(),
        evidence.getExtractedJson(),
        evidence.getLocked(),
        evidence.getLockedAt(),
        evidence.getCreatedAt(),
        evidence.getUpdatedAt());
  }
}
