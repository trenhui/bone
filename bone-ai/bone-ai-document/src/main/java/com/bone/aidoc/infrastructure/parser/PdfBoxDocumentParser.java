package com.bone.aidoc.infrastructure.parser;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDFBox 解析器（第一批最小 IR 默认实现——详设 §2.5）。
 *
 * <p>按页提取文本（{@code setSortByPosition(true)} 保留阅读顺序），页码供条款 span 定位。 仅处理文本层 PDF；扫描件 OCR 属第二批（sidecar
 * 契约接入后由 Docling/OCR 解析器承担）。
 */
public class PdfBoxDocumentParser implements DocumentParser {

  private static final Logger log = LoggerFactory.getLogger(PdfBoxDocumentParser.class);

  @Override
  public ParsedDocument parse(InputStream input, String sourceRef) {
    try (PDDocument document = PDDocument.load(input)) {
      int pageCount = document.getNumberOfPages();
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      List<String> pageTexts = new ArrayList<>(pageCount);
      for (int page = 1; page <= pageCount; page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        pageTexts.add(stripper.getText(document));
      }
      return ParsedDocument.of(pageCount, pageTexts, "pdfbox");
    } catch (IOException e) {
      throw new IllegalArgumentException("PDF parse failed: " + sourceRef, e);
    }
  }

  @Override
  public String provider() {
    return "pdfbox";
  }
}
