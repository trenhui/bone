package com.pkh.cloud.auth.infrastructure.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.util.SaResult;
import com.bone.core.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.pkh.cloud.auth.application.enums.ErrorCodeConstants.AUTH_TOKEN_EXPIRED;

@RestControllerAdvice
public class GlobalExceptionHandler {
    //未登录成功异常处理
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result> handlerNotLoginException(NotLoginException nle)
            throws Exception {

        // 打印堆栈，以供调试
        nle.printStackTrace();

        // 判断场景值，定制化异常信息
        HttpStatus httpStatus = HttpStatus.UNAUTHORIZED; // 默认状态码 401

        String message = "";
        if(nle.getType().equals(NotLoginException.NOT_TOKEN)) {
            message = "未能读取到有效 token";
        }
        else if(nle.getType().equals(NotLoginException.INVALID_TOKEN)) {
            message = "token 无效";
        }
        else if(nle.getType().equals(NotLoginException.TOKEN_TIMEOUT)) {
            message = "token 已过期";
        }
        else if(nle.getType().equals(NotLoginException.BE_REPLACED)) {
            message = "token 已被顶下线";
        }
        else if(nle.getType().equals(NotLoginException.KICK_OUT)) {
            message = "token 已被踢下线";
        }
        else if(nle.getType().equals(NotLoginException.TOKEN_FREEZE)) {
            message = "token 已被冻结";
        }
        else if(nle.getType().equals(NotLoginException.NO_PREFIX)) {
            message = "未按照指定前缀提交 token";
        }
        else {
            message = "当前会话未登录";
        }
        Result<Object> error = Result.error(AUTH_TOKEN_EXPIRED.getCode(), message);
        // 返回给前端
        return new ResponseEntity<>(error, httpStatus);
    }




    //全局异常拦截
    @ExceptionHandler
    public Result handlerException(Exception e) {
        e.printStackTrace();
        return Result.error(e.getMessage());
    }

}
