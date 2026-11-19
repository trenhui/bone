package com.bone.procurement.contract.domain.gateway;

import com.bone.procurement.contract.domain.model.ParsedContract;
import java.io.InputStream;

/** 合同解析出站端口：委托 bone-ai-document 完成解析与跨页条款切分，结果以本上下文自有 DTO 表达。 */
public interface ContractParsePort {

  /**
   * @throws com.bone.procurement.contract.domain.exception.ContractParseFailedException 解析失败时
   *     fail-closed
   */
  ParsedContract parse(InputStream input, String tenantId, String sourceRef, String docId);
}
