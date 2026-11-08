package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.request.ClaimPageReq;
import com.bone.claim.adapter.web.dto.request.SubmitClaimReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim.ClaimItemDto;
import com.bone.claim.domain.model.claim.ClaimCase;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 协议 DTO ↔ Command/Resp 组装器（MapStruct，ADP-0030 读侧与 E-13.5 入站命名约定）。
 *
 * <p>枚举直接透传（协议与领域共用同一枚举类，delivery/05 对外枚举真源在序列化层由 {@code status()} 投影保证）。
 */
@Mapper(componentModel = "spring")
public interface ClaimCaseAssembler {

  ClaimCaseAssembler INSTANCE = Mappers.getMapper(ClaimCaseAssembler.class);

  @Mapping(target = "idempotencyKey", ignore = true)
  @Mapping(target = "batchNo", ignore = true)
  SubmitClaim toSubmitClaim(SubmitClaimReq req);

  ClaimItemDto toClaimItemDto(SubmitClaimReq.ClaimItemReq req);

  List<ClaimItemDto> toClaimItemDtos(List<SubmitClaimReq.ClaimItemReq> reqs);

  default ClaimCaseResp toClaimCaseResp(ClaimCase claim) {
    return ClaimCaseResp.of(claim.getId(), claim.getClaimNo(), claim.externalStatus());
  }

  default List<com.bone.claim.domain.model.claim.valueobject.ClaimStatus> statusesOf(
      ClaimPageReq req) {
    return req.statuses() == null ? List.of() : req.statuses();
  }
}
