package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.assembler.ClaimReportAssembler;
import com.bone.claim.adapter.web.dto.request.AppendCaseReq;
import com.bone.claim.adapter.web.dto.response.ClaimCaseResp;
import com.bone.claim.adapter.web.dto.response.ClaimReportResp;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.ClaimReportApplicationService;
import com.bone.claim.application.ClaimReportApplicationService.AppendCaseCommand;
import com.bone.claim.application.ClaimReportApplicationService.ReportDetail;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.core.exception.BizException;
import com.bone.core.idempotency.IdempotencyService;
import com.bone.core.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 报案接口契约测试：<b>HTTP 四态</b>（201 / 404 / 422 / 400）+ 信封 code 与状态码一致 + 幂等键必传 + 授权面。
 *
 * <p><b>为什么这层必须单独钉</b>：后两层（应用/领域）全部用 {@code BizException} 表达失败，状态码映射只活在 {@code
 * ClaimErrors.DEFAULT_HTTP_STATUS} 与 Controller 注解里。这两处没有编译期约束：映射漏登记在类加载时才抛、{@code @ResponseStatus}
 * 写错要等真实请求才暴露。而前端 {@code i18n} 拿 {@code errorCode} 找文案、并按 HTTP 状态决定提示形态 —— 少一态就是一段「静默失败」的用户路径。
 *
 * <p><b>四态的语义分工</b>（错一个就会把「业务前提不成立」报成「你参数写错了」）：
 *
 * <ul>
 *   <li>201 —— 追加成功，且信封 {@code code} 必须是 201（API 规范 §3.1 要求信封 code 等于 HTTP 状态码）；
 *   <li>404 —— 报案不存在<b>或跨租户同号</b>，两者不区分（防枚举）；
 *   <li>422 —— 报案已关闭：请求合法、有权限，是「这次事故不再受理新赔案」这一业务前提不成立；
 *   <li>400 —— 请求体携带保单维度字段：形状问题，不是业务前提。
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ClaimReportControllerTest {

  private static final String REPORT_NO = "RP20261122ABCDEFGH1234";
  private static final String IDEM = "idem-append-1";

  @Mock private ClaimReportApplicationService claimReportApplicationService;
  @Mock private ClaimReportAssembler claimReportAssembler;
  @Mock private IdempotencyService idempotencyService;
  @Mock private ClaimIdempotencyProperties idempotencyProperties;
  @InjectMocks private ClaimReportController controller;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private HttpServletRequest httpRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    // lenient：400 用例在取 method/URI 之前就抛出（这正是「拒绝发生在幂等重放之前」的判据），
    // 严格模式会把这两个共用桩判成 UnnecessaryStubbing
    org.mockito.Mockito.lenient().when(request.getMethod()).thenReturn("POST");
    org.mockito.Mockito.lenient()
        .when(request.getRequestURI())
        .thenReturn("/api/v1/claims/reports/" + REPORT_NO + "/cases");
    return request;
  }

  private AppendCaseReq validRequest() {
    AppendCaseReq req = new AppendCaseReq();
    req.setInvoiceCode("04400001");
    req.setInvoiceNo("12345678");
    return req;
  }

  @Nested
  @DisplayName("HTTP 四态")
  class HttpStatusContract {

    @Test
    @DisplayName("201：追加成功，信封 code=201 且带赔案号与报案号")
    void appendReturns201() {
      AppendCaseCommand command = mock(AppendCaseCommand.class);
      when(claimReportAssembler.toAppendCommand(REPORT_NO, IDEM, validRequest()))
          .thenReturn(command);
      when(idempotencyService.replay(any(), any(), any(), any(), any()))
          .thenReturn(Optional.empty());
      when(claimReportApplicationService.appendCase(command))
          .thenReturn(new SubmittedClaim(1002L, "CL20261122NEWCASE", REPORT_NO));
      when(claimReportAssembler.toCaseResp(any()))
          .thenReturn(ClaimCaseResp.of(1002L, "CL20261122NEWCASE", REPORT_NO, "PENDING_EVIDENCE"));

      ApiResponse<ClaimCaseResp> body =
          controller.appendCase(REPORT_NO, IDEM, validRequest(), httpRequest());

      assertThat(body.getSuccess()).isTrue();
      // 信封 code 与 HTTP 201 必须一致：客户端若按 code 判断，200 会被当成「更新成功」而非「已创建」
      assertThat(body.getCode()).isEqualTo(HttpStatus.CREATED.value());
      assertThat(body.getData().claimNo()).isEqualTo("CL20261122NEWCASE");
      assertThat(body.getData().reportNo()).isEqualTo(REPORT_NO);
    }

    @Test
    @DisplayName("404 / 422 / 400：三个报案错误码的 HTTP 状态映射已登记且各就各位")
    void reportErrorCodesMapToExpectedStatuses() {
      assertThat(ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, REPORT_NO).getCode())
          .as("不存在与跨租户同号一律 404（防枚举）")
          .isEqualTo(404);
      assertThat(ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_CLOSED, REPORT_NO).getCode())
          .as("422 而非 409：请求合法，是业务前提不成立")
          .isEqualTo(422);
      assertThat(
              ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_CONTEXT_MISMATCH, "policyNo").getCode())
          .as("400 而非 422：请求体形状问题，不是业务前提")
          .isEqualTo(400);
    }

    @Test
    @DisplayName("不存在 / 已关闭由应用层抛出，Controller 不吞不改（异常原样上抛给全局处理器）")
    void domainFailuresPropagate() {
      AppendCaseCommand command = mock(AppendCaseCommand.class);
      when(claimReportAssembler.toAppendCommand(REPORT_NO, IDEM, validRequest()))
          .thenReturn(command);
      when(idempotencyService.replay(any(), any(), any(), any(), any()))
          .thenReturn(Optional.empty());
      when(claimReportApplicationService.appendCase(command))
          .thenThrow(ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_CLOSED, REPORT_NO));

      assertThatThrownBy(
              () -> controller.appendCase(REPORT_NO, IDEM, validRequest(), httpRequest()))
          .isInstanceOf(BizException.class)
          .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_REPORT_CLOSED);
    }
  }

  @Nested
  @DisplayName("400 的落点：保单维度字段出现即拒")
  class RejectsContextOverride {

    @Test
    @DisplayName("请求体带 policyNo → 400 CONTEXT_MISMATCH，且不进入应用层")
    void rejectsPolicyNo() throws Exception {
      AppendCaseReq req =
          objectMapper.readValue(
              "{\"invoiceCode\":\"04400001\",\"invoiceNo\":\"12345678\",\"policyNo\":\"EG20260001\"}",
              AppendCaseReq.class);

      assertThatThrownBy(() -> controller.appendCase(REPORT_NO, IDEM, req, httpRequest()))
          .isInstanceOf(BizException.class)
          .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_REPORT_CONTEXT_MISMATCH)
          .hasMessageContaining("policyNo");

      verify(claimReportApplicationService, never()).appendCase(any());
    }

    @Test
    @DisplayName("拒绝发生在幂等重放之前：不能让一个非法请求先被判定为「重放命中」")
    void rejectsBeforeIdempotencyLookup() throws Exception {
      AppendCaseReq req =
          objectMapper.readValue(
              "{\"invoiceCode\":\"04400001\",\"invoiceNo\":\"12345678\",\"outInsureTime\":\"2026-03-12T10:00:00Z\"}",
              AppendCaseReq.class);

      assertThatThrownBy(() -> controller.appendCase(REPORT_NO, IDEM, req, httpRequest()))
          .isInstanceOf(BizException.class);

      verify(idempotencyService, never()).replay(any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("幂等重放")
  class Replay {

    @Test
    @DisplayName("命中历史响应 → 原样返回快照体，不再次建案")
    @SuppressWarnings("unchecked")
    void replaysSnapshot() {
      AppendCaseCommand command = mock(AppendCaseCommand.class);
      when(claimReportAssembler.toAppendCommand(REPORT_NO, IDEM, validRequest()))
          .thenReturn(command);
      ApiResponse<ClaimCaseResp> snapshot =
          ApiResponse.success(
              HttpStatus.CREATED.value(),
              ClaimCaseResp.of(1002L, "CL20261122NEWCASE", REPORT_NO, "PENDING_EVIDENCE"));
      when(idempotencyService.replay(any(), any(), any(), any(), any()))
          .thenReturn(
              Optional.of(
                  new IdempotencyService.ReplayedResponse(
                      HttpStatus.CREATED.value(), "/api/v1/claims/1002", snapshot)));

      ApiResponse<ClaimCaseResp> body =
          controller.appendCase(REPORT_NO, IDEM, validRequest(), httpRequest());

      assertThat(body).isSameAs(snapshot);
      verify(claimReportApplicationService, never()).appendCase(any());
    }

    @Test
    @DisplayName("首建成功要登记快照，且 Location 指向赔案详情（重放时前端能拿到同一跳转）")
    void remembersSnapshotWithLocation() {
      AppendCaseCommand command = mock(AppendCaseCommand.class);
      when(claimReportAssembler.toAppendCommand(REPORT_NO, IDEM, validRequest()))
          .thenReturn(command);
      when(idempotencyService.replay(any(), any(), any(), any(), any()))
          .thenReturn(Optional.empty());
      when(claimReportApplicationService.appendCase(command))
          .thenReturn(new SubmittedClaim(1002L, "CL20261122NEWCASE", REPORT_NO));
      when(claimReportAssembler.toCaseResp(any()))
          .thenReturn(ClaimCaseResp.of(1002L, "CL20261122NEWCASE", REPORT_NO, "PENDING_EVIDENCE"));
      when(idempotencyProperties.getTtl()).thenReturn(Duration.ofDays(30));

      controller.appendCase(REPORT_NO, IDEM, validRequest(), httpRequest());

      verify(idempotencyService)
          .remember(
              eq(IDEM),
              any(),
              eq("POST"),
              any(),
              eq(command),
              argThat(r -> "/api/v1/claims/1002".equals(r.location())),
              any());
    }
  }

  @Nested
  @DisplayName("路由与授权面")
  class RoutingAndAuth {

    @Test
    @DisplayName("基路径 /api/v1/claims/reports；两个端点路径与 HTTP 方法明确")
    void pathsAreExplicit() throws Exception {
      assertThat(ClaimReportController.class.getAnnotation(RequestMapping.class).value())
          .containsExactly("/api/v1/claims/reports");

      Method detail = ClaimReportController.class.getMethod("detail", String.class);
      assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{reportNo}");

      Method append =
          ClaimReportController.class.getMethod(
              "appendCase",
              String.class,
              String.class,
              AppendCaseReq.class,
              HttpServletRequest.class);
      assertThat(append.getAnnotation(PostMapping.class).value())
          .containsExactly("/{reportNo}/cases");
      // @ResponseStatus 缺失 ⇒ HTTP 200 而信封 code=201，正是 §3.1 明令禁止的自相矛盾
      assertThat(append.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("授权沿用赔案资源权限：读走 claim:claims:read，写走 claim:claims:write")
    void authorizationReusesClaimResource() throws Exception {
      Method detail = ClaimReportController.class.getMethod("detail", String.class);
      assertThat(detail.getAnnotation(PreAuthorize.class).value())
          .isEqualTo("hasAuthority('claim:claims:read')");

      Method append =
          ClaimReportController.class.getMethod(
              "appendCase",
              String.class,
              String.class,
              AppendCaseReq.class,
              HttpServletRequest.class);
      assertThat(append.getAnnotation(PreAuthorize.class).value())
          .as("追加赔案本质仍是建案，单开权限码会让两套授权面需要同步维护")
          .isEqualTo("hasAuthority('claim:claims:write')");
    }

    @Test
    @DisplayName("Idempotency-Key 请求头必传：required=true（缺失由框架直接 400，服务端不代生成）")
    void idempotencyKeyHeaderIsMandatory() throws Exception {
      Method append =
          ClaimReportController.class.getMethod(
              "appendCase",
              String.class,
              String.class,
              AppendCaseReq.class,
              HttpServletRequest.class);
      Parameter[] parameters = append.getParameters();
      RequestHeader header = null;
      for (Parameter parameter : parameters) {
        RequestHeader candidate = parameter.getAnnotation(RequestHeader.class);
        if (candidate != null) {
          header = candidate;
        }
      }
      assertThat(header).as("appendCase 必须有 @RequestHeader 参数（幂等键）").isNotNull();
      assertThat(header.value()).isEqualTo(IdempotencyService.IDEMPOTENCY_KEY_HEADER);
      assertThat(header.required()).as("改成 required=false 会让缺键请求静默跳过幂等，重试即重复建案").isTrue();
    }
  }

  @Nested
  @DisplayName("报案详情")
  class Detail {

    @Test
    @DisplayName("返回报案头 + 名下赔案清单（空清单也照常返回：报案存在就是存在）")
    void detailReturnsHeaderAndCases() {
      // 用真实 ReportDetail（record 是 final）：mock 一个 record 会让「空清单」这类断言变成桩的自我确认
      ClaimReport report =
          ClaimReport.open(
              9001L,
              42L,
              "EG20260001",
              7L,
              "hash-abc",
              "OUTPATIENT",
              Instant.parse("2026-03-12T10:00:00Z"),
              "v3",
              "");
      ReportDetail detail = new ReportDetail(report, List.of());
      ClaimReportResp resp =
          new ClaimReportResp(
              report.getReportNo(),
              report.getPolicyNo(),
              report.getPlanId(),
              report.getClaimType(),
              report.getOutInsureTime(),
              report.status().name(),
              null,
              0,
              detail.allCasesFinished(),
              List.of());
      when(claimReportApplicationService.detail(report.getReportNo())).thenReturn(detail);
      when(claimReportAssembler.toResp(detail)).thenReturn(resp);

      ApiResponse<ClaimReportResp> body = controller.detail(report.getReportNo());

      assertThat(body.getSuccess()).isTrue();
      assertThat(body.getCode()).isEqualTo(200);
      assertThat(body.getData().reportNo()).isEqualTo(report.getReportNo());
      assertThat(body.getData().reportNo()).startsWith("RP");
      assertThat(body.getData().cases()).isEmpty();
      assertThat(body.getData().caseCount()).isZero();
      // 0 笔赔案不等于「全部结案」：报案刚建、还没追加时前端不能显示「已完成」
      assertThat(body.getData().allCasesFinished()).isFalse();
    }
  }
}
