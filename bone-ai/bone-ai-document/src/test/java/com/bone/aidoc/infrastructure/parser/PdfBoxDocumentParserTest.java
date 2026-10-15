package com.bone.aidoc.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.ParsedDocument;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

/** PdfBoxDocumentParser：真实 PDF 解析链（PDFBox 写入 → 解析 → 页码/文本）。 */
class PdfBoxDocumentParserTest {

  @Test
  void parses_real_pdf_page_text() throws IOException {
    byte[] pdf = createOnePagePdf("Clause 1: Delivery\nWithin 30 days.\n");
    PdfBoxDocumentParser parser = new PdfBoxDocumentParser();
    ParsedDocument parsed = parser.parse(new ByteArrayInputStream(pdf), "test.pdf");

    assertThat(parsed.provider()).isEqualTo("pdfbox");
    assertThat(parsed.pageCount()).isEqualTo(1);
    assertThat(parsed.pageTexts().get(0)).contains("Clause 1: Delivery");
  }

  @Test
  void counts_pages() throws IOException {
    byte[] pdf = createTwoPagePdf();
    PdfBoxDocumentParser parser = new PdfBoxDocumentParser();
    ParsedDocument parsed = parser.parse(new ByteArrayInputStream(pdf), "two.pdf");
    assertThat(parsed.pageCount()).isEqualTo(2);
    assertThat(parsed.pageTexts()).hasSize(2);
  }

  private byte[] createOnePagePdf(String text) throws IOException {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(50, 700);
        int line = 0;
        for (String s : text.split("\n")) {
          cs.newLineAtOffset(0, -16 * (line == 0 ? 0 : 1));
          cs.showText(s);
          line++;
        }
        cs.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private byte[] createTwoPagePdf() throws IOException {
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int i = 0; i < 2; i++) {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
          cs.beginText();
          cs.setFont(PDType1Font.HELVETICA, 12);
          cs.newLineAtOffset(50, 700);
          cs.showText("Page " + (i + 1) + " content");
          cs.endText();
        }
      }
      doc.save(out);
      return out.toByteArray();
    }
  }
}
