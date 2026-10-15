package com.bone.aidoc.application;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import com.bone.aidoc.domain.model.DocumentIR;
import com.bone.aidoc.domain.service.ClauseSplitter;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文档解析应用服务（第一批最小 IR）：解析 → 条款切片 + span → DocumentIR。
 *
 * <p>解析器经 SPI 注入（默认 PDFBox；Tika/Docling 就绪即可互换）。sidecar 超时降级 （{@code partial=true} → 编排转
 * HITL）为后续批次，本服务不伪造降级语义。
 */
public class DocumentParseService {

  private static final Logger log = LoggerFactory.getLogger(DocumentParseService.class);

  private final DocumentParser parser;

  public DocumentParseService(DocumentParser parser) {
    this.parser = parser;
  }

  /**
   * 解析文档并产出最小 IR。
   *
   * @param input 文档流
   * @param tenantId 租户
   * @param sourceRef 来源引用
   * @param docId 文档标识；为空时自动生成
   */
  public DocumentIR parse(InputStream input, String tenantId, String sourceRef, String docId) {
    ParsedDocument parsed = parser.parse(input, sourceRef);
    List<DocumentClause> clauses = ClauseSplitter.split(parsed);
    String id = (docId == null || docId.isBlank()) ? UUID.randomUUID().toString() : docId;
    log.info(
        "Document parsed: doc={}, tenant={}, pages={}, clauses={}, provider={}",
        id,
        tenantId,
        parsed.pageCount(),
        clauses.size(),
        parsed.provider());
    return DocumentIR.of(
        id, tenantId, sourceRef, parsed.pageCount(), "PDFBOX_TEXT", parsed.provider(), clauses);
  }
}
