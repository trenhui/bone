package com.bone.tools.codegen;

import com.bone.core.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 项目的启动类
 *
 * @author 芋道源码
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CodeGenServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeGenServerApplication.class, args);
    }

}
