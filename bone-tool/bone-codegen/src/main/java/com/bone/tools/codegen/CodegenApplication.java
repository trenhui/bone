package com.bone.tools.codegen;

import com.bone.core.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 代码生成服务启动类
 *
 * @author bone
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CodegenApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodegenApplication.class, args);
    }

}
