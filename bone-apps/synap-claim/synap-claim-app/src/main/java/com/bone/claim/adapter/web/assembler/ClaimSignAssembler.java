package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.response.SignBatchResp;
import com.bone.claim.adapter.web.dto.response.SignTrackResp;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.SignTrackLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/** 签收响应装配单一真源。 */
@Mapper(componentModel = "spring")
public interface ClaimSignAssembler {
  ClaimSignAssembler INSTANCE = Mappers.getMapper(ClaimSignAssembler.class);

  // 聚合的 id 来自基类 getId()，与 DTO 的 batchId 不同名：不显式映射会静默输出 null。
  @Mapping(target = "batchId", source = "id")
  SignBatchResp toResp(ClaimSignBatch batch);

  SignTrackResp toResp(SignTrackLog track);
}
