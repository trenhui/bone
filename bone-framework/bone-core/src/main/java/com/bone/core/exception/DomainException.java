package com.bone.core.exception;

/**
 * 领域异常 —— 聚合/值对象内部的不变式违反（"实体已启用""账户已锁定""编码已存在"等）。
 *
 * <p><b>为何域层用它而不是 {@link BizException}</b>：域层不依赖传输层是正确的分层 （{@code domain} 零框架依赖）。{@code
 * BizException} 的首参是 HTTP 状态 int，属传输语义， 域层不该知道。但域异常<b>必须被翻译成带码的传输异常</b>，否则它会一路冒泡到 {@code
 * GlobalExceptionHandler} 的 {@code @ExceptionHandler(Exception.class)} 兜底 ⇒ 被报成 <b>HTTP
 * 500</b>，把「状态冲突 / 参数非法」混进 5xx 错误预算、污染告警与 SLO。
 *
 * <p><b>翻译路径</b>（两条，优先级从高到低）：
 *
 * <ol>
 *   <li><b>调用点catch 后翻译</b>（推荐，语义最准）：应用层 {@code catch (DomainException e) → throw
 *       XxxErrors.of(XXX_CONFLICT, e.getMessage())} —— 已在iam 等模块使用（域→码的映射只有应用层知道）。
 *   <li><b>兜底</b>：未被翻译的域异常由 {@code GlobalExceptionHandler} 的
 *       {@code @ExceptionHandler(DomainException.class)} 分支接住， 映射为 <b>400 + {@code
 *       COMMON_DOMAIN_RULE_VIOLATION}</b>（RFC 9457 的 problem type 语义），保证「至少不是 500」。
 * </ol>
 *
 * <p><b>为什么默认 400 而不是 409</b>：HTTP 无法从异常本身区分「状态冲突」与「入参非法」， 猜 409 会误导客户端重试（冲突类语义要求客户端改状态而非重试）。
 * 精确语义必须靠路径 ① 显式给出码——<b>兜底只负责「不是 500」这一条底线</b>。
 */
public class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public DomainException(String message) {
    super(message);
  }

  public DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
