package com.bone.claim.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * CSV 导出的字节级回归测试。
 *
 * <p><b>为什么需要</b>：导出的三个坑都属「打开文件才发现」的缺陷——UTF-8 无 BOM 时 Excel 按 ANSI 解析中文全乱码、
 * 含逗号/引号的单元格未转义导致列错位、CRLF/LF 混用影响下游解析。这些只能在生成端断言。
 *
 * <p><b>变异判别力</b>：去掉 BOM 前缀 → {@link #body_startsWithUtf8Bom()} 转红；escape 里去掉引号双写 → {@link
 * #quoteInsideCell_isDoubled()} 转红；改用 {@code System.lineSeparator()} → {@link #rows_useCrlf()} 转红。
 */
class CsvExportTest {

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return new String(response.getBody(), StandardCharsets.UTF_8);
  }

  @Test
  void body_startsWithUtf8Bom() {
    ResponseEntity<byte[]> response =
        CsvExport.response("policies", new String[] {"保单号"}, List.of());

    byte[] body = response.getBody();
    // byte 是有符号的（0xEF 存为 -17），先按无符号还原再比较
    assertThat(new int[] {body[0] & 0xFF, body[1] & 0xFF, body[2] & 0xFF})
        .as("首三字节须是 UTF-8 BOM (EF BB BF)，否则 Excel 中文乱码")
        .containsExactly(0xEF, 0xBB, 0xBF);
  }

  @Test
  void headerAndFilename_areSet() {
    ResponseEntity<byte[]> response =
        CsvExport.response("policies", new String[] {"保单号"}, List.of());

    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .as("文件名 ASCII + 当日后缀")
        .startsWith("attachment; filename=\"policies-")
        .endsWith(".csv\"");
    assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
  }

  @Test
  void plainCells_areNotQuoted() {
    String body =
        bodyOf(
            CsvExport.response(
                "t", new String[] {"a", "b"}, List.<String[]>of(new String[] {"x", "y"})));

    assertThat(body).contains("a,b\r\nx,y\r\n");
  }

  @Test
  void commaAndNewlineCells_areQuoted() {
    String body =
        bodyOf(
            CsvExport.response(
                "t",
                new String[] {"h"},
                List.<String[]>of(new String[] {"a,b"}, new String[] {"line1\nline2"})));

    assertThat(body).contains("\"a,b\"").contains("\"line1\nline2\"");
  }

  @Test
  void quoteInsideCell_isDoubled() {
    String body =
        bodyOf(
            CsvExport.response(
                "t", new String[] {"h"}, List.<String[]>of(new String[] {"say \"hi\""})));

    assertThat(body).contains("\"say \"\"hi\"\"\"");
  }

  @Test
  void nullCell_rendersEmpty() {
    String body =
        bodyOf(CsvExport.response("t", new String[] {"h"}, List.<String[]>of(new String[] {null})));

    assertThat(body).doesNotContain("null").contains("h\r\n\r\n");
  }

  @Test
  void rows_useCrlf() {
    String body =
        bodyOf(
            CsvExport.response(
                "t",
                new String[] {"a"},
                List.<String[]>of(new String[] {"1"}, new String[] {"2"})));

    assertThat(body.split("\r\n", -1)).as("每行以 CRLF 结尾，末尾留空段").hasSize(4);
    assertThat(body).doesNotContain("\n\r");
  }

  @Test
  void formulaLeadingCells_areNeutralized() {
    String body =
        bodyOf(
            CsvExport.response(
                "t",
                new String[] {"h1", "h2"},
                List.<String[]>of(new String[] {"=1+1", "@SUM(A1,A2)"})));

    assertThat(body).contains("h1,h2\r\n'=1+1,\"'@SUM(A1,A2)\"\r\n");
  }
}
