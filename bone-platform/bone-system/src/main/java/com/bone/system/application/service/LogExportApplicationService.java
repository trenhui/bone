package com.bone.system.application.service;

import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.handler.LogQueryHandler;
import com.bone.system.application.query.qry.LogExportQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 日志导出（MVP-09「日志查询」）。
 *
 * <p>导出为 CSV 而非 JSON：日志的消费者是人和 Excel，CSV 双击即开。
 */
@Component
@RequiredArgsConstructor
public class LogExportApplicationService {

  private static final String CSV_HEADER = "id,level,service,traceId,content,createdAt";

  private final LogQueryHandler logQueryHandler;

  /** 按条件导出日志为 CSV 字节。 */
  public byte[] exportCsv(LogExportQuery qry) {
    List<LogDTO> logs = logQueryHandler.listForExport(qry);
    StringBuilder sb = new StringBuilder(Math.max(1024, logs.size() * 128));
    // UTF-8 BOM：Excel 默认按本地编码打开 CSV，没有 BOM 中文会乱码
    sb.append('\uFEFF').append(CSV_HEADER).append('\n');
    for (LogDTO log : logs) {
      sb.append(log.getId())
          .append(',')
          .append(csv(log.getLogLevel()))
          .append(',')
          .append(csv(log.getServiceName()))
          .append(',')
          .append(csv(log.getTraceId()))
          .append(',')
          .append(csv(log.getContent()))
          .append(',')
          .append(log.getCreatedAt() == null ? "" : log.getCreatedAt().toString())
          .append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** CSV 单元格转义：包裹引号并把内部引号翻倍（RFC 4180）。 */
  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
