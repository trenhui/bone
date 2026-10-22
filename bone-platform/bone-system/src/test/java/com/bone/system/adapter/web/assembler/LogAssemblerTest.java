package com.bone.system.adapter.web.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.system.adapter.web.dto.request.LogPageReq;
import com.bone.system.application.query.qry.LogPageQuery;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** {@link LogAssembler#toQuery(LogPageReq)} 的时间范围解析验证。 */
public class LogAssemblerTest {

  private final LogAssembler assembler = Mappers.getMapper(LogAssembler.class);

  @Test
  public void toQueryParsesIsoTimestamp() {
    LogPageReq req = new LogPageReq();
    req.setKeyword("boom");
    req.setLogLevel("ERROR");
    req.setServiceName("bone-system");
    req.setStartTime("2026-01-01T00:00:00");
    req.setEndTime("2026-01-31T23:59:59");
    req.setPage(2);
    req.setSize(20);

    LogPageQuery qry = assembler.toQuery(req);

    assertEquals("boom", qry.getKeyword());
    assertEquals("ERROR", qry.getLogLevel());
    assertEquals("bone-system", qry.getServiceName());
    assertEquals(2, qry.getPage());
    assertEquals(20, qry.getSize());
    assertEquals(LocalDateTime.parse("2026-01-01T00:00:00"), qry.getStartTime());
    assertEquals(LocalDateTime.parse("2026-01-31T23:59:59"), qry.getEndTime());
  }

  @Test
  public void toQueryParsesPageDatePickerFormat() {
    // 前端日期控件输出 yyyy-MM-dd HH:mm:ss，必须也能解析（兼容性分支）
    LogPageReq req = new LogPageReq();
    req.setStartTime("2026-03-04 05:06:07");
    req.setEndTime("2026-03-05 08:09:10");

    LogPageQuery qry = assembler.toQuery(req);

    assertEquals(
        LocalDateTime.parse(
            "2026-03-04 05:06:07", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        qry.getStartTime());
    assertEquals(
        LocalDateTime.parse(
            "2026-03-05 08:09:10", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
        qry.getEndTime());
  }

  @Test
  public void toQueryNullTimesYieldNull() {
    LogPageQuery qry = assembler.toQuery(new LogPageReq());
    assertNull(qry.getStartTime());
    assertNull(qry.getEndTime());
  }

  @Test
  public void toQueryNullRequestYieldsEmptyQuery() {
    LogPageQuery qry = assembler.toQuery(null);
    assertNotNull(qry);
    assertNull(qry.getKeyword());
    assertNull(qry.getStartTime());
  }
}
