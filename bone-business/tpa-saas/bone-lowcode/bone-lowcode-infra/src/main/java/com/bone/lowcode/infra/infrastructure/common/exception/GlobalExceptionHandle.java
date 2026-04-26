package com.bone.lowcode.infra.infrastructure.common.exception;


import com.bone.core.exception.ServiceException;
import com.bone.core.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;


@ControllerAdvice
@ResponseBody
@Slf4j
public class GlobalExceptionHandle {


    @ExceptionHandler(ServiceException.class)
    public Result handleOCRException(ServiceException e) {
        log.error("全局异常处理,出现自定义异常:", e);

        Result r = Result.error(e.getCode(), "系统异常,tip:" + e.getMessage());
        return r;
    }

    //用于捕获@RequestBody类型参数触发校验规则抛出的异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleJSR303Exception(MethodArgumentNotValidException e) {
        log.error("全局异常处理,出现@RequestBody场景参数校验异常=============", e);

        List<String> errorList = new ArrayList<>();
        int index = 0;
        Map<String, String> errorMap = new LinkedHashMap<>();
        BindingResult result = e.getBindingResult();
        for (FieldError fieldError : result.getFieldErrors()) {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            errorMap.put(++index + ":" + field, message);
            errorList.add(message);
        }
        log.error("@RequestBody场景参数校验异常:{}", errorMap);
        Result r = Result.error(500, String.join(";", errorList));
        return r;
    }

    //用于捕获@RequestParam/@PathVariable类型参数触发校验规则抛出的异常
    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolationException(ConstraintViolationException e) {
        log.error("全局异常处理,出现@RequestParam/@PathVariable场景参数校验异常=============", e);

        List<String> errorList = new ArrayList<>();
        int index = 0;
        Map<String, String> errorMap = new LinkedHashMap<>();
        Set<ConstraintViolation<?>> conSet = e.getConstraintViolations();
        for (ConstraintViolation<?> con : conSet) {
            String propertyPath = con.getPropertyPath().toString();
            String message = con.getMessage();
            errorMap.put(++index + ":" + propertyPath.substring(propertyPath.indexOf(".") + 1), message);
            errorList.add(message);
        }
        log.error("@RequestParam/@PathVariable场景参数校验异常:{}", errorMap);
        Result r = Result.error(500, String.join(";", errorList));
        return r;
    }

    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("全局异常处理,出现异常:", e);

        Result r = Result.error(500, "系统异常,tip:" + e.getMessage());
        return r;
    }
}
