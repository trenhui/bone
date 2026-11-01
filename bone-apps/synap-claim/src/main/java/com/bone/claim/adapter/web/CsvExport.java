package com.bone.claim.adapter.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 配置面列表的 CSV 导出（保单 / 计划 / 额度账户）。
 *
 * <p><b>为什么是 CSV 而不是 Excel</b>：后端不引 POI 等重依赖即可产出通用格式；Excel 打开 CSV 的 唯一坑是编码——UTF-8 无 BOM 时 Excel 按
 * ANSI 解析，中文全变乱码，故首字节固定写 {@code U+FEFF}（BOM），Excel 双击直开不乱码，程序侧解析也大多忽略 BOM。
 *
 * <p><b>值用原始枚举码</b>（{@code ACTIVE} 而非「已发布」）：CSV 是数据交换格式而非展示层，
 * 原始码无歧义、可再导入、且不会与前端的展示文案（唯一真源）产生两份会漂移的拷贝。
 *
 * <p><b>文件名只用 ASCII</b>：带中文的 {@code Content-Disposition} 在部分浏览器/代理下要走 RFC 5987
 * 编码，收益不值那个复杂度；日期后缀已足够区分批次。
 */
public final class CsvExport {

  private CsvExport() {}

  /**
   * 组装 CSV 响应。
   *
   * @param fileBaseName 文件名主干（ASCII，如 {@code policies}），最终文件名带当日日期
   * @param header 表头（中文可读）
   * @param rows 数据行（与表头列序一致；单元格值用原始枚举码）
   */
  public static ResponseEntity<byte[]> response(
      String fileBaseName, String[] header, List<String[]> rows) {
    StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM：Excel 不乱码
    appendRow(sb, header);
    for (String[] row : rows) {
      appendRow(sb, row);
    }
    String filename = fileBaseName + "-" + LocalDate.now() + ".csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** 单行拼装：逗号分隔 + CRLF 换行（RFC 4180）。 */
  private static void appendRow(StringBuilder sb, String[] cells) {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(escape(cells[i]));
    }
    sb.append("\r\n");
  }

  /**
   * 单元格转义：含逗号 / 引号 / 换行时整体加引号，内部引号双写（RFC 4180）。
   *
   * <p>{@code null} 输出空串而非字面量 {@code "null"}——导出给业务看的文件里不应出现 Java 词汇。
   */
  static String escape(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    boolean quote =
        value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    String escaped = value.replace("\"", "\"\"");
    return quote ? "\"" + escaped + "\"" : escaped;
  }
}
