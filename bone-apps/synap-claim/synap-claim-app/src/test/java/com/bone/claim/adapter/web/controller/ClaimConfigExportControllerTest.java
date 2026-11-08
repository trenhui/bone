package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimConfigApplicationService;
import com.bone.claim.application.PolicyDetailApplicationService;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 配置面 CSV 导出的契约与字节级钉子。
 *
 * <p><b>为什么锁 {@code produces} 与返回类型</b>：前端按 {@code responseType: 'blob'} 直接取响应体， 端点若套上 {@code
 * ApiResponse} 信封，下载下来的就是一段 JSON 而不是 CSV，且前端不会报错——属静默失败。
 */
class ClaimConfigExportControllerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return new String(response.getBody(), StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("两个导出端点锁死路径 / produces / 只读权限 / 文件流返回类型")
  void locksExportContract() throws NoSuchMethodException {
    assertExport(PolicyConfigController.class.getMethod("export", String.class, String.class));
    assertExport(PlanConfigController.class.getMethod("export", String.class));
  }

  private static void assertExport(Method method) {
    GetMapping mapping = method.getAnnotation(GetMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/export");
    assertThat(mapping.produces()).containsExactly("text/csv;charset=UTF-8");
    PreAuthorize authority = method.getAnnotation(PreAuthorize.class);
    assertThat(authority).isNotNull();
    assertThat(authority.value()).contains("claim:policy:read");
    assertThat(method.getReturnType()).isEqualTo(ResponseEntity.class);
    assertThat(method.getGenericReturnType().getTypeName()).contains("ResponseEntity<byte[]>");
  }

  @Test
  @DisplayName("保单导出：筛选条件原样透传、21 列表头、BOM、含逗号字段被转义")
  void exportPoliciesRendersCsv() {
    ClaimConfigApplicationService service = mock(ClaimConfigApplicationService.class);
    PolicyDetailApplicationService detail = mock(PolicyDetailApplicationService.class);
    when(service.exportPolicies("KW", "ACTIVE"))
        .thenReturn(
            List.of(
                new ClaimConfigApplicationService.PolicyView(
                    "P-1",
                    "名称,含逗号",
                    "保司",
                    "分公司",
                    "投保公司",
                    "BIZ",
                    "门诊",
                    "GROUP",
                    NOW,
                    NOW,
                    30,
                    "v1",
                    "[]",
                    "INS-1",
                    "ACTIVE",
                    "NEED",
                    "NOT_NEED",
                    false,
                    NOW,
                    NOW,
                    1L)));

    ResponseEntity<byte[]> response =
        new PolicyConfigController(service, detail).export("KW", "ACTIVE");

    verify(service).exportPolicies("KW", "ACTIVE");
    assertThat(
            new int[] {
              response.getBody()[0] & 0xFF,
              response.getBody()[1] & 0xFF,
              response.getBody()[2] & 0xFF
            })
        .containsExactly(0xEF, 0xBB, 0xBF);
    assertThat(response.getHeaders().getContentDisposition().toString())
        .contains("policies-")
        .contains(".csv");
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("text/csv");
    String body = bodyOf(response);
    assertThat(body.split("\r\n", -1)[0].split(",", -1)).hasSize(21);
    assertThat(body).contains("\"名称,含逗号\"").contains("P-1").doesNotContain("null");
  }

  @Test
  @DisplayName("计划导出复用 listPlans 全量结果，额度用 toPlainString")
  void exportPlansReusesList() {
    ClaimConfigApplicationService service = mock(ClaimConfigApplicationService.class);
    when(service.listPlans("P-1"))
        .thenReturn(
            List.of(
                new ClaimConfigApplicationService.PlanView(
                    "1",
                    "P-1",
                    "PLAN-1",
                    "计划,含逗号",
                    new BigDecimal("1000.00"),
                    30,
                    "v1",
                    "ACTIVE",
                    NOW,
                    NOW,
                    1L)));

    ResponseEntity<byte[]> response = new PlanConfigController(service).export("P-1");

    verify(service).listPlans("P-1");
    String body = bodyOf(response);
    assertThat(body.split("\r\n", -1)[0].split(",", -1)).hasSize(11);
    assertThat(body).contains("\"计划,含逗号\"").contains("1000.00").doesNotContain("null");
  }

  @Test
  @DisplayName("空结果只输出表头，不出现 null 字面量")
  void emptyExportRendersHeaderOnly() {
    ClaimConfigApplicationService service = mock(ClaimConfigApplicationService.class);
    when(service.listPlans("P-EMPTY")).thenReturn(List.of());

    String body = bodyOf(new PlanConfigController(service).export("P-EMPTY"));

    assertThat(body.split("\r\n", -1)).hasSize(2);
    assertThat(body.split("\r\n", -1)[0].split(",", -1)).hasSize(11);
    assertThat(body).doesNotContain("null");
  }
}
