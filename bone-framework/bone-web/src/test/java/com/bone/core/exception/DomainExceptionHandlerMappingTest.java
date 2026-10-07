package com.bone.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.common.CommonErrorCodes;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.ProblemDetail;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * {@link GlobalExceptionHandler#domainExceptionHandler(DomainException)} 的映射契约。
 *
 * <p><b>为何用反射</b>：handler 的 {@code problemResponse(int, String, String)} 是 <b>private</b>， 而
 * handler 本身<b>没有独立于 Spring 的公开映射入口</b>（方法与 private 辅助函数均未公开） ⇒ 不反射就只能靠「起 Spring 上下文发HTTP
 * 请求」间接验证，成本高且无法定位到本类。
 *
 * <p>反射的代价已在此写明：private 方法改名会让本测试报NoSuchMethodException。 若将来 {@code problemResponse} 被提取为可注入的
 * {@code ProblemDetailFactory}，应改用注入而非反射。
 *
 * <p><b>为何必须有此测试</b>：2026-10-07 实测——域层抛 {@code DomainException} 是正确分层， 但 handler 此前<b>没有</b>对应分支 ⇒
 * 全仓约 170 处域异常（含子类 {@code StateConflictException}， 如「账户已处于启用状态」）全部冒泡到 {@code Exception.class} 兜底 ⇒
 * 被报成 <b>HTTP 500</b>， 既误导客户端重试（重试无用，状态不会变），又把业务错误混进 5xx 错误预算、污染告警与 SLO。
 */
class DomainExceptionHandlerMappingTest {

  private ResponseEntity<ApiResponse<?>> invokeHandler(Throwable ex) throws Exception {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    Method m =
        GlobalExceptionHandler.class.getDeclaredMethod(
            "domainExceptionHandler", DomainException.class);
    m.setAccessible(true);
    @SuppressWarnings("unchecked")
    ResponseEntity<ApiResponse<?>> resp = (ResponseEntity<ApiResponse<?>>) m.invoke(handler, ex);
    return resp;
  }

  @Test
  @DisplayName("域异常兜底为 400 + COMMON_VALIDATION_FAILED（与 masterdata/system/iam/generator 四个服务一致）")
  void mapsTo400WithStableCode() throws Exception {
    ResponseEntity<ApiResponse<?>> resp = invokeHandler(new DomainException("账户已处于启用状态"));

    assertEquals(400, resp.getStatusCode().value());
    ApiResponse<?> body = resp.getBody();
    assertNotNull(body, "响应体不可为空，否则前端拿不到 errorCode");
    // ⚠️ ApiResponse.code 是 **Integer HTTP 状态**，不是业务码；业务码在 data（ProblemDetail）里。
    assertEquals(400, body.getCode());
    // ★ 为什么必须是 VALIDATION_FAILED 而不是 CONFLICT：
    //   台账 §2：COMMON_CONFLICT = 409「版本/状态冲突」、COMMON_VALIDATION_FAILED = 400「参数校验失败」；
    //   本分支状态码是 400，配 CONFLICT 属语义错配；且另4 个服务自己的 DomainException 分支
    //   都是 400+VALIDATION_FAILED ⇒ 同一异常跨服务必须同码（RFC 9457），否则前端 i18n 取不到翻译、监控裂成两个口径。
    assertEquals(CommonErrorCodes.VALIDATION_FAILED, problemErrorCode(body));
  }

  @Test
  @DisplayName("★ 绝不落到 5xx（4xx 才是业务错误，否则污染 5xx 告警与 SLO）")
  void neverBecomesServerError() throws Exception {
    ResponseEntity<ApiResponse<?>> resp = invokeHandler(new DomainException("只有新建状态的支付单可以提交支付"));

    int status = resp.getStatusCode().value();
    assertTrue(status >= 400 && status < 500, "域异常是可纠正的业务错误，必须 4xx，实际=" + status);
  }

  @Test
  @DisplayName("message 原样透传：域异常 message 是本仓业务语义，非底层原文，无需脱敏")
  void messagePassesThrough() throws Exception {
    String msg = "编码已被已删除实体占用: CODE1（该实体曾发布/归档，编码不可复用）";

    ResponseEntity<ApiResponse<?>> resp = invokeHandler(new DomainException(msg));

    ApiResponse<?> body = resp.getBody();
    assertNotNull(body);
    assertEquals(msg, body.getMessage());
  }

  @Test
  @DisplayName("子类由同一分支覆盖：一个 handler覆盖全部域异常（实测约 170 处）")
  void subclassCoveredBySameBranch() throws Exception {
    // Spring 的 @ExceptionHandler 按类型匹配 ⇒ 任何 DomainException 子类无需单独分支。
    // 本模块无法引用 blueprint 的 StateConflictException（跨模块依赖），故用本包内的
    // 测试子类验证「继承即被覆盖」这一机制本身；
    // 生产侧的 StateConflictException extends DomainException 由继承链保证。
    class SubDomainException extends DomainException {
      SubDomainException(String message) {
        super(message);
      }
    }

    ResponseEntity<ApiResponse<?>> resp = invokeHandler(new SubDomainException("状态不允许该操作"));

    assertEquals(400, resp.getStatusCode().value());
    assertEquals(CommonErrorCodes.VALIDATION_FAILED, problemErrorCode(resp.getBody()));
  }

  /**
   * 从响应的 {@code data}（ProblemDetail）里取稳定业务码。
   *
   * <p><b>刻意不在此硬编码某个具体码</b>（上一版硬编码了 CONFLICT）：helper 硬编码会让「换码」
   * 这件事在多个用例里同时失败、看不出是哪条语义决策导致的。改为只断言「有码」，具体码由每个 用例显式断言，失败时直接看出是哪条语义决策。
   */
  private static String problemErrorCode(ApiResponse<?> body) {
    assertNotNull(body.getData(), "error 响应的 data 必须承载 ProblemDetail");
    ProblemDetail problem = (ProblemDetail) body.getData();
    assertNotNull(problem.getErrorCode(), "ProblemDetail.errorCode 不可为空，否则前端无 i18n 键可用");
    return problem.getErrorCode();
  }
}
