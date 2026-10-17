package com.bone.aidoc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.domain.model.DocumentClause;
import com.bone.aidoc.domain.model.DocumentIR;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DocumentParseService：解析 → 条款切片 → 最小 IR（doc_id/租户/页码/span）。 */
class DocumentParseServiceTest {

  private DocumentParser parser;
  private DocumentParseService service;

  @BeforeEach
  void setUp() {
    parser = mock(DocumentParser.class);
    service = new DocumentParseService(parser);
  }

  @Test
  void produces_minimal_ir() {
    when(parser.provider()).thenReturn("pdfbox");
    when(parser.parse(any(InputStream.class), any()))
        .thenReturn(
            ParsedDocument.of(1, List.of("第一条 甲方：采购方\n付款条款。\n第二条 乙方：供应商\n交付条款。\n"), "pdfbox"));

    InputStream in = new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
    DocumentIR ir = service.parse(in, "t1", "contract/001.pdf", "doc-1");

    assertThat(ir.docId()).isEqualTo("doc-1");
    assertThat(ir.tenantId()).isEqualTo("t1");
    assertThat(ir.sourceRef()).isEqualTo("contract/001.pdf");
    assertThat(ir.pageCount()).isEqualTo(1);
    assertThat(ir.parseMode()).isEqualTo("PDFBOX_TEXT");
    assertThat(ir.provider()).isEqualTo("pdfbox");
    assertThat(ir.partial()).isFalse();
    assertThat(ir.clauses()).hasSize(2);
    assertThat(ir.clauses().get(0)).isInstanceOf(DocumentClause.class);
    assertThat(ir.clauses().get(0).spanStart()).isEqualTo(0);
  }

  @Test
  void auto_generates_doc_id_when_blank() {
    when(parser.parse(any(InputStream.class), any()))
        .thenReturn(ParsedDocument.of(1, List.of("plain text"), "pdfbox"));
    DocumentIR ir = service.parse(new ByteArrayInputStream(new byte[0]), "t1", "a.pdf", null);
    assertThat(ir.docId()).isNotBlank();
  }

  @Test
  void derives_docling_parse_mode_from_provider() {
    when(parser.provider()).thenReturn("docling");
    when(parser.parse(any(InputStream.class), any()))
        .thenReturn(ParsedDocument.of(1, List.of("p1"), "docling"));

    DocumentIR ir =
        service.parse(
            new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "t1", "d.pdf", "doc-2");
    assertThat(ir.parseMode()).isEqualTo("DOCLING");
    assertThat(ir.provider()).isEqualTo("docling");
  }
}
