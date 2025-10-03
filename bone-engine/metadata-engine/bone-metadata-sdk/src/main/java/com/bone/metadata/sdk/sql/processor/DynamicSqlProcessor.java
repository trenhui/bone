package com.bone.metadata.sdk.sql.processor;

import com.bone.metadata.sdk.domain.exception.SqlProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态 SQL 处理类，支持 where、if 和 foreach 等标签的解析和处理。
 * 增强版：支持 ${} 占位符替换
 */
@Slf4j
public class DynamicSqlProcessor implements SqlProcessor {

    // 增强版标签正则表达式，支持嵌套标签
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "<([/]?)(\\w+)(?:\\s+((?:\"[^\"]*\"|'[^']*'|[^>])*))?>(.*?)</\\2>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // ${} 占位符正则表达式
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    // SpEL 表达式解析器，用于处理 <if> 标签中的条件
    private final ExpressionParser spelParser = new SpelExpressionParser();

    private final Map<String, DirectiveHandler> handlers = new HashMap<>();

    public DynamicSqlProcessor() {
        handlers.put("where", new WhereHandler());
        handlers.put("if", new IfHandler(spelParser));
        handlers.put("foreach", new ForeachHandler());
    }

    @Override
    public ProcessedSql process(String templateId,String sqlTemplate, Map<String, Object> params) {

        log.debug("sqlTemplate:"+sqlTemplate);
        if (sqlTemplate == null) {
            throw new SqlProcessingException("SQL template cannot be null");
        }
        if (params == null) {
            log.warn("Input params map is null, using empty map");
            params = new HashMap<>();
        }

        validateSqlStructure(sqlTemplate);
        validateParameters(params);

        SqlContext context = new SqlContext(params);
        processFragment(sqlTemplate, context);

        // 处理 ${} 占位符
        String finalSql = replacePlaceholders(context.getSql(), context.getParams());

        return new ProcessedSql(finalSql, context.getParams());
    }

    /**
     * 替换 ${} 占位符为实际值
     */
    private String replacePlaceholders(String sql, Map<String, Object> params) {
        if (sql == null || params == null) {
            return sql;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Object value = params.get(placeholder);

            if (value != null) {
                // 验证替换值的安全性
                validatePlaceholderValue(placeholder, value.toString());
                matcher.appendReplacement(result, value.toString());
            } else {
                log.warn("Placeholder ${} not found in parameters, keeping as is", placeholder);
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 验证占位符值的安全性，防止SQL注入
     */
    private void validatePlaceholderValue(String placeholder, String value) {
        // ORDER BY 子句的特殊验证
        if ("orderBy".equals(placeholder)) {
            if (!value.matches("^[a-zA-Z0-9_,\\s]+(\\s+(ASC|DESC))?$")) {
                throw new SqlProcessingException("Invalid orderBy value: " + value);
            }
        }
        // 其他占位符的通用验证
        else if (value.matches(".*[;'\"].*")) {
            throw new SqlProcessingException("Potential SQL injection detected in placeholder: " + placeholder);
        }
    }

    private void validateParameters(Map<String, Object> params) {
        params.forEach((k, v) -> {
            if (v instanceof String str && (str.contains(";") || str.matches("(?i).*\\b(SELECT|DROP)\\b.*"))) {
                throw new SqlProcessingException("Invalid parameter value: " + k);
            }
        });
    }

    public void validateSqlStructure(String sql) {
        if (sql.contains(";")) {
            throw new SqlProcessingException("Multiple statements not allowed");
        }
        if (sql.matches("(?i).*\\b(drop|alter|truncate)\\b.*")) {
            throw new SqlProcessingException("Dangerous SQL operation detected");
        }
    }

    private void processFragment(String fragment, SqlContext context) {
        log.debug("Processing fragment: {}", fragment);
        if (fragment == null || fragment.trim().isEmpty()) {
            return;
        }

        Matcher matcher = TAG_PATTERN.matcher(fragment);
        int lastPos = 0;

        while (matcher.find()) {
            context.append(fragment.substring(lastPos, matcher.start()));  // Add prefix content

            boolean isClosing = !matcher.group(1).isEmpty();
            String tagName = matcher.group(2).toLowerCase();
            String attributes = matcher.group(3);
            String content = matcher.groupCount() >= 4 ? matcher.group(4) : "";

            log.debug("Found tag: <{}{} {}>, content: [{}]", isClosing ? "/" : "", tagName, attributes, content);

            if (isClosing) {
                context.closeTag(tagName);  // Close the tag
            } else {
                Map<String, String> attrs = parseAttributes(attributes);  // Parse attributes
                DirectiveHandler handler = handlers.get(tagName);
                if (handler != null) {
                    TagNode node = new TagNode(tagName, attrs, content);
                    handler.handle(node, context);  // Handle the tag
                } else {
                    context.append(fragment.substring(matcher.start(), matcher.end()));  // Keep the tag as is
                }
            }
            lastPos = matcher.end();
        }
        context.append(fragment.substring(lastPos));  // Add suffix content
    }

    interface DirectiveHandler {
        void handle(TagNode node, SqlContext context);
    }

    static class WhereHandler implements DirectiveHandler {
        public void handle(TagNode node, SqlContext context) {
            context.pushClosure(content -> {
                String trimmed = content.trim()
                        .replaceAll("(?i)^AND\\s+", "")
                        .replaceAll("(?i)^OR\\s+", "");
                return trimmed.isEmpty() ? "" : "WHERE " + trimmed;
            });
            context.startTagContent();
            processChildren(node.content, context);
            context.closeTag("where");
        }
    }

    static class IfHandler implements DirectiveHandler {
        private final ExpressionParser spelParser;

        IfHandler(ExpressionParser spelParser) {
            this.spelParser = spelParser;
        }

        public void handle(TagNode node, SqlContext context) {
            String testExpr = (String) node.attrs.get("test");
            StandardEvaluationContext spelContext = new StandardEvaluationContext();
            spelContext.setRootObject(context.params);
            spelContext.setPropertyAccessors(Collections.singletonList(new MapAccessor()));
            Expression expr = this.spelParser.parseExpression(testExpr);

            Boolean result = false;
            try {
                result = expr.getValue(spelContext, Boolean.class);
            } catch (Exception e) {
                // Log warning and treat as false if evaluation fails
                log.warn("SpEL evaluation failed for expression: {}. Treating as false. Error: {}", testExpr, e.getMessage());
            }

            if (Boolean.TRUE.equals(result)) {
                DynamicSqlProcessor.processChildren(node.content, context);
            }
        }
    }

    static class ForeachHandler implements DirectiveHandler {
        public void handle(TagNode node, SqlContext context) {
            String itemsExpr = node.attrs.get("items");
            String open = node.attrs.getOrDefault("open", "");
            String close = node.attrs.getOrDefault("close", "");
            String separator = node.attrs.getOrDefault("separator", ",");

            try {
                Object collection = context.params.get(itemsExpr);
                if (collection instanceof Iterable) {
                    List<String> parts = new ArrayList<>();
                    int index = 0;
                    for (Object item : (Iterable<?>) collection) {
                        // 跳过null项
                        if (item == null) {
                            continue;
                        }
                        String paramName = itemsExpr + "_" + index++;
                        context.addParam(paramName, item);
                        parts.add(":" + paramName);
                    }
                    if (!parts.isEmpty()) {
                        String joined = open + String.join(separator, parts) + close;
                        context.append(node.content.replace(":item", joined));
                    }
                }
            } catch (Exception e) {
                throw new SqlProcessingException("FOREACH items evaluation failed", e);
            }
        }
    }

    static class SqlContext {
        private final StringBuilder sql = new StringBuilder();
        private final StringBuilder currentTagContent = new StringBuilder();
        private final Map<String, Object> params;
        private final Deque<Closure> closures = new ArrayDeque<>();
        private boolean isProcessingTagContent = false;

        SqlContext(Map<String, Object> params) {
            this.params = new HashMap<>();
            if (params != null) {
                // 过滤掉null值
                params.forEach((key, value) -> {
                    if (value != null) {
                        this.params.put(key, value);
                    }
                });
            }
        }

        void append(String text) {
            if (isProcessingTagContent) {
                currentTagContent.append(text);
            } else {
                sql.append(text);
            }
        }

        void startTagContent() {
            isProcessingTagContent = true;
            currentTagContent.setLength(0);
        }

        String finishTagContent() {
            isProcessingTagContent = false;
            String content = currentTagContent.toString();
            currentTagContent.setLength(0);
            return content;
        }

        void addParam(String name, Object value) {
            if (value != null) {
                params.put(name, value);
            }
        }

        void pushClosure(Closure closure) {
            closures.push(closure);
        }

        void closeTag(String tagName) {
            if (!closures.isEmpty()) {
                Closure closure = closures.pop();
                boolean wasProcessingTagContent = isProcessingTagContent;
                String content = wasProcessingTagContent ? finishTagContent() : sql.toString();
                String processed = closure.apply(content);
                if (!wasProcessingTagContent) {
                    sql.setLength(0);
                }
                sql.append(processed);
                log.debug("Closed tag: {}, result: [{}]", tagName, processed);
            } else {
                log.warn("No closures to pop for tag: {}", tagName);
            }
        }

        String getSql() {
            return sql.toString().replaceAll("\\s+", " ").trim();
        }

        Map<String, Object> getParams() {
            return Collections.unmodifiableMap(params);
        }
    }

    record TagNode(String name, Map<String, String> attrs, String content) {
    }

    interface Closure {
        String apply(String content);
    }

    private static Map<String, String> parseAttributes(String attrStr) {
        Map<String, String> attrs = new HashMap<>();
        if (attrStr == null || attrStr.trim().isEmpty()) {
            return attrs;
        }

        Pattern pattern = Pattern.compile(
                "(\\w+)\\s*=\\s*\"([^\"]*)\"|(\\w+)\\s*=\\s*'([^']*)'|(\\w+)\\s*=\\s*([^\\s>]+)"
        );
        Matcher matcher = pattern.matcher(attrStr);
        while (matcher.find()) {
            String key = null;
            String value = null;
            if (matcher.group(1) != null) {
                key = matcher.group(1);
                value = matcher.group(2);
            } else if (matcher.group(3) != null) {
                key = matcher.group(3);
                value = matcher.group(4);
            } else if (matcher.group(5) != null) {
                key = matcher.group(5);
                value = matcher.group(6);
            }
            if (key != null && value != null) {
                attrs.put(key, value);
            }
        }
        return attrs;
    }

    private static void processChildren(String content, SqlContext context) {
        new DynamicSqlProcessor().processFragment(content, context);
    }
}