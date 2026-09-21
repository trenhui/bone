package com.bone.system.application;

import com.bone.system.application.query.dto.LogDto;
import com.bone.system.application.query.qry.LogExportQuery;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 日志导出（MVP-09「日志查询」）。
 *
 * <p>导出为 CSV 而非 JSON：日志的消费者是人和 Excel，CSV 双击即开。
 *
 * <p><b>为何与 {@link SystemLogApplicationService} 分开</b>：它不持有事务、不触碰聚合，只做「读结果 → CSV 字节」的协议转换；但它又不是
 * adapter——同一个导出契约将来还要被定时任务（每日归档）复用，放在应用层可以让 两个入站适配器共享它（E-3.4 的"多个入站适配器共享用例集合"）。若将来始终只有 HTTP
 * 一个入口，应把它降级到 {@code adapter/web} 再由 Controller 直接编排。
 */
@Service
@RequiredArgsConstructor
public class LogExportApplicationService {

  private static final String CSV_HEADER = "id,level,service,traceId,content,createdAt";

  private final SystemLogApplicationService systemLogApplicationService;

  /** 按条件导出日志为 CSV 字节。 */
  public byte[] exportCsv(LogExportQuery query) {
    List<LogDto> logs = systemLogApplicationService.listForExport(query);
    StringBuilder sb = new StringBuilder(Math.max(1024, logs.size() * 128));
    // UTF-8 BOM：Excel 默认按本地编码打开 CSV，没有 BOM 中文会乱码
    sb.append('\uFEFF').append(CSV_HEADER).append('\n');
    for (LogDto log : logs) {
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
