package com.bone.system.adapter.web.assembler;

import com.bone.system.adapter.web.dto.request.CreateLogReq;
import com.bone.system.adapter.web.dto.request.LogExportReq;
import com.bone.system.adapter.web.dto.request.LogPageReq;
import com.bone.system.adapter.web.dto.response.LogResp;
import com.bone.system.application.command.CreateLogCommand;
import com.bone.system.application.query.dto.LogDto;
import com.bone.system.application.query.qry.LogExportQuery;
import com.bone.system.application.query.qry.LogPageQuery;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.mapstruct.Mapper;

/** 日志的 HTTP ↔ application 转换边界。 */
@Mapper
public interface LogAssembler {

  CreateLogCommand toCommand(CreateLogReq req);

  LogPageQuery toQuery(LogPageReq req);

  LogResp toResp(LogDto dto);

  /**
   * 导出请求 → 导出查询。
   *
   * <p>不交给 MapStruct：字符串 → {@link LocalDateTime} 的解析要兼容两种格式，注解映射表达不了，硬生成反而会在
   * 格式不匹配时抛转换异常（500）而不是按「无时间条件」处理。
   */
  default LogExportQuery toExportQuery(LogExportReq req) {
    LogExportQuery qry = new LogExportQuery();
    if (req == null) {
      return qry;
    }
    qry.setServiceName(req.getService());
    qry.setLogLevel(req.getLevel());
    qry.setStartTime(parse(req.getStartTime()));
    qry.setEndTime(parse(req.getEndTime()));
    return qry;
  }

  default Instant toInstant(LocalDateTime ldt) {
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }

  static LocalDateTime parse(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String value = text.trim();
    try {
      return LocalDateTime.parse(value);
    } catch (DateTimeParseException ignored) {
      // 兼容页面日期控件的 yyyy-MM-dd HH:mm:ss 输出
      return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
  }
}
