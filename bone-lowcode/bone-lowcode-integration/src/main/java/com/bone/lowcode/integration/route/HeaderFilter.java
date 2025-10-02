package com.bone.lowcode.integration.route;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class HeaderFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 检查请求头中的所有字段
        httpRequest.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            String headerValue = httpRequest.getHeader(headerName);

            // 清理请求头中的非法字符（换行符、回车符等）
            if (headerValue != null) {
                String cleanedHeaderValue = headerValue.replaceAll("[\\n\\r]", "");
                httpRequest.setAttribute(headerName, cleanedHeaderValue); // 使用清理后的值
            }
        });

        // 继续处理请求
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // 销毁
    }
}
