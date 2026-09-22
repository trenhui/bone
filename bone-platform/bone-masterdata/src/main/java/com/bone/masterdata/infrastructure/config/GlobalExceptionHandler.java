package com.bone.masterdata.infrastructure.config;

import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.exception.SystemException;
import com.bone.core.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final int NOT_IMPLEMENTED_CODE = 501;

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BizException e) {
    return ResponseEntity.status(resolveHttpStatus(e.getCode()))
        .body(ApiResponse.error(e.getCode(), e.getMessage()));
  }

  private static HttpStatus resolveHttpStatus(int code) {
    if (code == NOT_IMPLEMENTED_CODE) {
      return HttpStatus.NOT_IMPLEMENTED;
    }
    HttpStatus resolved = HttpStatus.resolve(code);
    if (resolved != null && (resolved.is4xxClientError() || resolved.is5xxServerError())) {
      return resolved;
    }
    return HttpStatus.BAD_REQUEST;
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, e.getMessage()));
  }

  /**
   * 并发下的唯一约束冲突 → 409。
   *
   * <p>应用层的前置 count 校验是 check-then-act，挡不住并发插入；真正的保证在数据库唯一索引， 这里把它统一翻译成 409 而不是漏成 500。
   */
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException e) {
    log.warn("唯一约束冲突: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, "记录已存在（唯一约束冲突）"));
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(400, e.getMessage()));
  }

  @ExceptionHandler(SystemException.class)
  public ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e) {
    log.error("系统异常: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(500, e.getMessage()));
  }

  /**
   * 请求入参不合法 → 400。
   *
   * <p>Bean Validation 失败、缺参、JSON 不可读、类型不匹配、非法参数都属于客户端错误； 若缺失这些分支，会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使客户端错误与服务端故障无法区分。
   */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
    log.warn("请求参数不合法: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error(400, "请求参数不合法: " + e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("未处理异常: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(500, "系统内部错误"));
  }

  /**
   * 请求地址不存在 → 404（而非被 catch-all 兜底成 500）。
   *
   * <p>Spring 6（Boot 3）下未匹配路由抛出 {@link NoResourceFoundException}；必须显式处理，否则会被
   * {@code @ExceptionHandler(Exception.class)} 兜底成 500，使「接口不存在」与「服务器内部错误」语义混淆。
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
    log.warn("No resource found: {}", e.getResourcePath());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error(404, "请求地址不存在: " + e.getResourcePath()));
  }
}
