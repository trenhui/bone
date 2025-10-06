package com.bone.integration.uitls;
import com.bone.integration.uitls.customMethod.MD5Util;
import com.bone.integration.uitls.customMethod.ScriptContext;
import com.bone.integration.core.log.LogSendManager;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import com.bone.integration.core.log.ConsoleLogger;
import org.apache.camel.Exchange;


@Slf4j
public class ScriptExecutor {
//        public static void main(String[] args) throws Exception {
//            // 假设从前端获取脚本内容和语言类型
//            String scriptContent = "def input = 'hello'; debug;return md5Util.md5(input)"; // Groovy 示例
//            String language = "groovy"; // 假设 Groovy 脚本
//
//            // 执行脚本
//            String model = execute(null, null,null, scriptContent, language);
//            System.out.println("Script ApiResponse: " + model);
//        }

    public static String execute(Exchange exchange, String debugConnId, Object body, Map<String, Object> headers, String expression, String language) throws Exception {
        // 根据脚本语言选择脚本引擎
        if ("groovy".equalsIgnoreCase(language)) {
            return executeGroovyScript(exchange, debugConnId, body, headers, expression);
        } else if ("qlExpression".equalsIgnoreCase(language)) {
            return executeQLExpressionScript(debugConnId,body, headers, expression);
        } else {
            throw new UnsupportedOperationException("Unsupported language: " + language);
        }
    }

    // 执行 Groovy 脚本的方法
    private static String executeGroovyScript(Exchange exchange, String debugConnId, Object body, Map<String, Object> headers, String expression) throws Exception {
        GroovyShell shell = new GroovyShell();

        // 传递静态 MD5Util 类给 Groovy 脚本
        shell.setVariable("md5Util", new MD5Util());

        // 从容器获取
        LogSendManager logSendManager = WebApplicationContextUtils.getBean(LogSendManager.class);

        // 创建请求对象，包含 header 和 body
        Map<String, Object> request = new HashMap<>();
        request.put("header", headers);  // 将 headers 映射传递给 Groovy 脚本
        request.put("body", body);
        shell.setVariable("request", request);

        // 传递 logger 给脚本
        ConsoleLogger consoleLogger = new ConsoleLogger(debugConnId, logSendManager);
        shell.setVariable("logger", consoleLogger);

        // 创建 Header 实例，并将其传递给脚本
        ScriptContext scriptContext = new ScriptContext(exchange);  // 使用模拟的 Header 类
        shell.setVariable("scriptContext", scriptContext);  // 将 Header 对象作为脚本中的变量传递

        // 执行脚本并返回结果
        Object result = shell.evaluate(expression);

        return result != null ? result.toString() : null;
    }


    // 执行 QL 脚本的方法
    private static String executeQLExpressionScript(String debugConnId,Object body, Map<String, Object> headers, String expression) {


        return null;
    }
}


