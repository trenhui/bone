package com.bone.system.application.query.qry;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 日志导出查询条件（MVP-09「日志查询」）。
 *
 * <p>读侧参数对象，不进入 domain。与 {@link LogPageQuery} 的区别：导出不带分页（{@code pageNum/pageSize}）， 改为 {@code
 * limit} 行数上限 + 可选时间范围。
 *
 * <p><b>为什么要有 limit</b>：SDK 的 {@code list()} 没有行数下推能力，不带上限的导出会把整表拉进内存。
 * 这里在应用层截断并如实返回条数，避免「导出」变成一次压垮服务的全表扫描。
 */
@Data
public class LogExportQuery {
  private String keyword;
  private String logLevel;
  private String serviceName;
  private LocalDateTime startTime;
  private LocalDateTime endTime;

  /** 单次导出的最大行数。 */
  private int limit = 10000;
}
