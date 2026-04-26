package com.bone.tpa.api.infrastructure.handler;

import com.bone.core.exception.ServiceException;
import com.bone.core.result.Result;
import com.bone.core.util.ExceptionUtil;
import com.bone.core.util.MetadataException;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Objects;

import static com.bone.core.enums.GlobalErrorCodeConstants.*;


/**
 * 全局异常处理器，将 Exception 翻译成 Result + 对应的异常编号
 */
@RestControllerAdvice
@Slf4j
public class ApiGlobalExceptionHandler {



    /**
     * 处理 SpringMVC 请求参数缺失
     * <p>
     * 例如说，接口上设置了 @RequestParam("xx") 参数，结果并未传递 xx 参数
     */
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public Result<?> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException ex) {
        log.warn("[missingServletRequestParameterExceptionHandler]", ex);
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数缺失:%s", ex.getParameterName()));
    }

    /**
     * 处理 SpringMVC 请求参数类型错误
     * <p>
     * 例如说，接口上设置了 @RequestParam("xx") 参数为 Integer，结果传递 xx 参数类型为 String
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> methodArgumentTypeMismatchExceptionHandler(MethodArgumentTypeMismatchException ex) {
        log.warn("[missingServletRequestParameterExceptionHandler]", ex);
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数类型错误:%s", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> illegalArgumentExceptionHandler(IllegalArgumentException ex) {
        log.warn("[illegalArgumentExceptionHandler]", ex);
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数类型错误:%s", ex.getMessage()));
    }

    /**
     * 处理 SpringMVC 参数校验不正确
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> methodArgumentNotValidExceptionExceptionHandler(MethodArgumentNotValidException ex) {
        log.warn("[methodArgumentNotValidExceptionExceptionHandler]", ex);
        FieldError fieldError = ex.getBindingResult().getFieldError();
        assert fieldError != null; // 断言，避免告警
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", fieldError.getDefaultMessage()));
    }

    /**
     * 处理 SpringMVC 参数绑定不正确，本质上也是通过 Validator 校验
     */
    @ExceptionHandler(BindException.class)
    public Result<?> bindExceptionHandler(BindException ex) {
        log.warn("[handleBindException]", ex);
        FieldError fieldError = ex.getFieldError();
        assert fieldError != null; // 断言，避免告警
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", fieldError.getDefaultMessage()));
    }

    /**
     * 处理 Validator 校验不通过产生的异常
     */
    @ExceptionHandler(value = ConstraintViolationException.class)
    public Result<?> constraintViolationExceptionHandler(ConstraintViolationException ex) {
        log.warn("[constraintViolationExceptionHandler]", ex);
        ConstraintViolation<?> constraintViolation = ex.getConstraintViolations().iterator().next();
        return Result.error(BAD_REQUEST.getCode(), String.format("请求参数不正确:%s", constraintViolation.getMessage()));
    }

    /**
     * 处理 Dubbo Consumer 本地参数校验时，抛出的 ValidationException 异常
     */
    @ExceptionHandler(value = ValidationException.class)
    public Result<?> validationException(ValidationException ex) {
        log.warn("[constraintViolationExceptionHandler]", ex);
        // 无法拼接明细的错误信息，因为 Dubbo Consumer 抛出 ValidationException 异常时，是直接的字符串信息，且人类不可读
        return Result.error(BAD_REQUEST.getCode(), "constraintViolationExceptionHandler");
    }

    /**
     * 处理 SpringMVC 请求地址不存在
     * <p>
     * 注意，它需要设置如下两个配置项：
     * 1. spring.mvc.throw-exception-if-no-handler-found 为 true
     * 2. spring.mvc.static-path-pattern 为 /statics/**
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> noHandlerFoundExceptionHandler(NoHandlerFoundException ex) {
        log.warn("[noHandlerFoundExceptionHandler]", ex);
        return Result.error(NOT_FOUND.getCode(), String.format("请求地址不存在:%s", ex.getRequestURL()));
    }

    /**
     * 处理 SpringMVC 请求方法不正确
     * <p>
     * 例如说，A 接口的方法为 GET 方式，结果请求方法为 POST 方式，导致不匹配
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> httpRequestMethodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException ex) {
        log.warn("[httpRequestMethodNotSupportedExceptionHandler]", ex);
        return Result.error(METHOD_NOT_ALLOWED.getCode(), String.format("请求方法不正确:%s", ex.getMessage()));
    }

    /**
     * 处理 Resilience4j 限流抛出的异常
     */
    public Result<?> requestNotPermittedExceptionHandler(HttpServletRequest req, Throwable ex) {
        log.warn("[requestNotPermittedExceptionHandler][url({}) 访问过于频繁]", req.getRequestURL(), ex);
        return Result.error(TOO_MANY_REQUESTS.getCode(), String.format("[requestNotPermittedExceptionHandler][url(%s) 访问过于频繁]", req.getRequestURL()));
    }

//    /**
//     * 处理 Spring Security 权限不足的异常
//     * <p>
//     * 来源是，使用 @PreAuthorize 注解，AOP 进行权限拦截
//     */
//    @ExceptionHandler(value = AccessDeniedException.class)
//    public Result<?> accessDeniedExceptionHandler(HttpServletRequest req, AccessDeniedException ex) {
//        return Result.error(FORBIDDEN.toString());
//    }

    /**
     * 处理业务异常 ServiceException
     * <p>
     * 例如说，商品库存不足，用户手机号已存在。
     */
    @ExceptionHandler(value = ServiceException.class)
    public Result<?> serviceExceptionHandler(ServiceException ex) {
        log.info("[serviceExceptionHandler]", ex);
        return Result.error(ex.getCode(), ex.getMessage());
    }


    /**
     * 处理元数据层异常 MetadataException
     * <p>
     * 表结构未找到等问题
     */
    @ExceptionHandler(value = MetadataException.class)
    public Result<?> metadataExceptionHandler(MetadataException ex) {
        log.info("MetadataException caught", ex);
        return Result.error(ex.getResultCode().getCode(), ex.getMessage());
    }


    /**
     * 处理业务异常 BizException
     * <p>
     * 参数错误，余额不足等
     */
    @ExceptionHandler(value = TpaBizException.class)
    public Result<?> bizExceptionHandler(TpaBizException ex) {
        log.info("BizException caught", ex);
        return Result.error(ex.getErrorCode().getCode(), ex.getMessage());
    }


    /**
     * 处理系统异常，兜底处理所有的一切
     */
    @ExceptionHandler(value = Exception.class)
    public Result<?> defaultExceptionHandler(HttpServletRequest req, Throwable ex) {
        // 情况一：处理表不存在的异常
        Result<?> tableNotExistsResult = handleTableNotExists(ex);
        if (tableNotExistsResult != null) {
            return tableNotExistsResult;
        }

        // 情况二：部分特殊的库的处理
        if (Objects.equals("io.github.resilience4j.ratelimiter.RequestNotPermitted", ex.getClass().getName())) {
            return requestNotPermittedExceptionHandler(req, ex);
        }

        Throwable cause = ex.getCause();
        if (cause instanceof InvocationTargetException) {
            log.error("[handleInvocationTargetException - cause]", cause);
            return Result.error(INTERNAL_SERVER_ERROR.getCode(), cause.getMessage());
        }

        // 情况三：处理异常
        log.error("[defaultExceptionHandler]", ex);
        // 插入异常日志
        this.createExceptionLog(req, ex);
        // 返回 ERROR Result
        return Result.error(INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMsg());
    }

    private void createExceptionLog(HttpServletRequest req, Throwable e) {
        // 插入错误日志
        //ApiErrorLog errorLog = new ApiErrorLog();
        try {
            // 初始化 errorLog
            //initExceptionLog(errorLog, req, e);
            // 执行插入 errorLog
            //  apiErrorLogFrameworkService.createApiErrorLog(errorLog);
        } catch (Throwable th) {
            // log.error("[createExceptionLog][url({}) log({}) 发生异常]", req.getRequestURI(), JSONUtil.toJsonStr(errorLog), th);
        }
    }


    /**
     * 处理 Table 不存在的异常情况
     *
     * @param ex 异常
     * @return 如果是 Table 不存在的异常，则返回对应的 Result
     */
    private Result<?> handleTableNotExists(Throwable ex) {
        String message = ExceptionUtil.getRootCauseMessage(ex);
        if (!message.contains("doesn't exist")) {
            return null;
        }
        return null;
    }

    @ExceptionHandler(SQLException.class)
    public Result<?> handleSQLException(SQLException ex) {
        log.error("[SQLException]", ex);
        return Result.error(INTERNAL_SERVER_ERROR.getCode(), ex.getMessage());
    }

}
