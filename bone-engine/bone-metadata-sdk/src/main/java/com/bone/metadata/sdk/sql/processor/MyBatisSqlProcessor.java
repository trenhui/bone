package com.bone.metadata.sdk.sql.processor;

import com.bone.metadata.sdk.domain.exception.SqlInjectionRiskException;
import com.bone.metadata.sdk.domain.exception.SqlProcessingException;
import com.bone.metadata.sdk.support.cache.FragmentCache;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.*;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * High-performance MyBatis-compatible SQL processor.
 * Supports dynamic SQL syntax with multi-level caching and security validation.
 */
public class MyBatisSqlProcessor implements SqlProcessor {
    private static final Logger log = LoggerFactory.getLogger(MyBatisSqlProcessor.class);

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "<([/]?)([\\w:]+)(?:\\s+((?:\"[^\"]*\"|'[^']*'|[^>])*))?>(.*?)</\\2>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([^}]+)}|\\$\\{([^}]+)}");
    //    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
//            "(\\w+)\\s*=\\s*['\"]([^'\"\\\\]*(?:\\\\.[^'\"\\\\]*)*)['\"]",
//            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|(\\S+))",
            Pattern.CASE_INSENSITIVE
    );
    
    // 用于匹配SQL片段定义的模式
    private static final Pattern SQL_FRAGMENT_PATTERN = Pattern.compile(
            "<sql\\s+id=\\\"([^\\\"]*)\\\">([\\s\\S]*?)</sql>",
            Pattern.CASE_INSENSITIVE
    );

    // 内部工具类，替代对StringUtils的依赖
    private static class SqlFunctions {
        public static boolean hasText(String str) {
            return str != null && !str.trim().isEmpty();
        }
        
        public static boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }
    }
    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "<include\\s+refid\\s*=\\s*['\"]([^'\"]+)['\"]\\s*/>",
            Pattern.CASE_INSENSITIVE);

    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final SqlConfigProperties properties;
    private Cache<String, Expression> expressionCache;
    private Cache<String, SqlNode> astCache;

    @Autowired
    public MyBatisSqlProcessor(SqlConfigProperties properties) {
        this.properties = properties;
        initCaches();
    }

    private void initCaches() {
        this.expressionCache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getSpelCacheSize())
                .expireAfterAccess(properties.getCache().getExpireHours(), TimeUnit.HOURS)
                .recordStats()
                .build();

        this.astCache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getAstCacheSize())
                .expireAfterAccess(properties.getCache().getExpireHours(), TimeUnit.HOURS)
                .recordStats()
                .build();
    }


    private void validateSpelExpressions(String sqlTemplate) {
        // Improved regex to catch incomplete expressions like "!=" or "!=" followed by whitespace
        Pattern incompleteSpelPattern = Pattern.compile("!=\\s*['\"]?$");
        Matcher matcher = incompleteSpelPattern.matcher(sqlTemplate);
        if (matcher.find()) {
            throw new SqlProcessingException(
                    "Incomplete SpEL expression detected. Found: '" + matcher.group() +
                            "'. Please ensure it's written as '!= '''."
            );
        }
    }

    @Override
    public ProcessedSql process(String templateId, String sqlTemplate, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("sqlTemplate： " + sqlTemplate);
            // 1. 预处理：修复不完整的SpEL表达式
            //String preprocessedSql = preprocessSpelExpressions(sqlTemplate);

            // Ensure params are not null
            Map<String, Object> finalParams = params != null ? params : new HashMap<>();

            // Log the parameters at the beginning of processing
            log.debug("Processing SQL template with params: {}", finalParams);

            // 2. Extract SQL fragments from template
            extractSqlFragments(templateId, sqlTemplate);

            // 3. Replace include tags
            String processedSql = processIncludeTags(templateId, sqlTemplate);

            log.debug("processedSql： " + processedSql);

            // 4. Validate SQL structure
            validateSqlStructure(processedSql);

            // 5. Validate parameters
            validateParameters(finalParams);

            // 6. Get or parse AST
            String cacheKey = generateCacheKey(templateId, processedSql);
            SqlNode ast = astCache.get(cacheKey, k -> parseSqlToAst(processedSql));

            // 7. Process SQL
            SqlContext context = new SqlContext(this, properties, finalParams);
            ast.process(context);

            // 8. Get final SQL
            String finalSql = context.getSql();
            validateSql(finalSql);

            if (log.isDebugEnabled()) {
                log.debug("SQL processed in {}ms: {}", System.currentTimeMillis() - startTime, finalSql);
                log.debug("Final Parameters: {}", context.getParams());
            }

            return new ProcessedSql(finalSql, context.getParams());
        } catch (Exception e) {
            log.error("SQL processing failed for template: {}", templateId, e);
            throw new SqlProcessingException("Failed to process SQL for template: " + templateId, e);
        }
    }

    private String preprocessSpelExpressions(String sqlTemplate) {
        // 修复不完整的SpEL表达式
        return sqlTemplate.replaceAll("test\\s*=\\s*['\"]([^'\"]*\\!=\\s*)(?=['\"])", "test=\"$1''\"");
    }

    private String generateCacheKey(String templateId, String sqlTemplate) {
        return templateId + "_" + DigestUtils.md5DigestAsHex(sqlTemplate.getBytes(StandardCharsets.UTF_8));
    }

    private void extractSqlFragments(String templateId, String sqlTemplate) {
        String className = (templateId != null && templateId.contains("."))
                ? templateId.substring(0, templateId.lastIndexOf('.'))
                : "<inline>";

        Matcher matcher = SQL_FRAGMENT_PATTERN.matcher(sqlTemplate);
        while (matcher.find()) {
            String fragmentId = matcher.group(1);
            String fragmentContent = matcher.group(2).trim();
            FragmentCache.putFragment(className, fragmentId, fragmentContent);
            log.debug("Extracted SQL fragment: class={}, id={}, content={}", className, fragmentId, fragmentContent);
        }
    }

    private String processIncludeTags(String templateId, String sqlTemplate) {
        String className = (templateId != null && templateId.contains("."))
                ? templateId.substring(0, templateId.lastIndexOf('.'))
                : "<inline>";

        Matcher matcher = INCLUDE_PATTERN.matcher(sqlTemplate);
        StringBuffer result = new StringBuffer();
        int depth = 0;
        final int MAX_DEPTH = 10;

        while (matcher.find()) {
            if (depth++ > MAX_DEPTH) {
                throw new SqlProcessingException("Maximum include depth exceeded for template: " + templateId);
            }

            String refId = matcher.group(1);
            String fragment = FragmentCache.getById( className + "." + refId);
            if (!SqlFunctions.hasText(fragment)) {
                log.warn("SQL fragment not found: class={}, id={}, leaving unchanged", className, refId);
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                String processedFragment = processIncludeTags(templateId, fragment);
                matcher.appendReplacement(result, Matcher.quoteReplacement(processedFragment));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private SqlNode parseSqlToAst(String sqlTemplate) {
        List<SqlNode> nodes = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        Matcher matcher = TAG_PATTERN.matcher(sqlTemplate);
        int lastPos = 0;

        while (matcher.find()) {
            if (lastPos < matcher.start()) {
                currentText.append(sqlTemplate, lastPos, matcher.start());
            }

            String tagName = matcher.group(2).toLowerCase();
            String attributesStr = matcher.group(3);
            String content = matcher.group(4);
            boolean isClosing = !matcher.group(1).isEmpty();

            if (!isClosing) {
                if (currentText.length() > 0) {
                    nodes.add(new TextNode(currentText.toString()));
                    currentText.setLength(0);
                }

                Map<String, String> attrs = parseAttributes(attributesStr);
                nodes.add(createNode(tagName, attrs, content));
            }

            lastPos = matcher.end();
        }

        if (lastPos < sqlTemplate.length()) {
            currentText.append(sqlTemplate.substring(lastPos));
        }

        if (currentText.length() > 0) {
            nodes.add(new TextNode(currentText.toString()));
        }

        return new CompositeNode(nodes);
    }

    private Map<String, String> parseAttributes(String attributesStr) {
        Map<String, String> attrs = new HashMap<>();
        if (SqlFunctions.hasText(attributesStr)) {
            Matcher attrMatcher = ATTRIBUTE_PATTERN.matcher(attributesStr);
            while (attrMatcher.find()) {
                String value = attrMatcher.group(3);  // 双引号值
                if (value == null) {
                    value = attrMatcher.group(4);     // 单引号值
                }
                if (value == null) {
                    value = attrMatcher.group(5);     // 无引号值
                }
                attrs.put(attrMatcher.group(1).toLowerCase(), value);
                log.debug("Parsed attribute: key={}, value={}", attrMatcher.group(1), value);
            }
        }
        return attrs;
    }

    private SqlNode createNode(String tagName, Map<String, String> attrs, String content) {
        switch (tagName.toLowerCase()) {
            case "if":
                return new IfNode(attrs.get("test"), content);
            case "where":
                return new WhereNode(content);
            case "foreach":
                return new ForeachNode(
                        attrs.get("collection"),
                        attrs.getOrDefault("item", "item"),
                        attrs.getOrDefault("index", "index"),
                        attrs.getOrDefault("open", ""),
                        attrs.getOrDefault("separator", ","),
                        attrs.getOrDefault("close", ""),
                        content);
            case "choose":
                return new ChooseNode(content);
            case "when":
                return new WhenNode(attrs.get("test"), content);
            case "otherwise":
                return new OtherwiseNode(content);
            case "set":
                return new SetNode(content);
            case "trim":
                return new TrimNode(
                        attrs.getOrDefault("prefix", ""),
                        attrs.getOrDefault("suffix", ""),
                        attrs.getOrDefault("prefixOverrides", ""),
                        attrs.getOrDefault("suffixOverrides", ""),
                        content);
            case "bind":
                return new BindNode(
                        attrs.get("name"),
                        attrs.get("value"),
                        content);
            default:
                return new TextNode("<" + tagName + ">" + content + "</" + tagName + ">");
        }
    }

    private boolean evaluateSpel(String expression, Map<String, Object> params) {
        try {
            if (!SqlFunctions.hasText(expression)) {
                log.warn("Empty SpEL expression.");
                return false;
            }

            log.debug("Evaluating SpEL expression: {}", expression);

            // 创建评估上下文
            StandardEvaluationContext evalContext = new StandardEvaluationContext();

            // 设置根对象为参数Map，这样可以直接访问嵌套属性
            evalContext.setRootObject(params);

            // 设置属性访问器
            evalContext.setPropertyAccessors(Arrays.asList(
                    new MapAccessor(),
                    new ReflectivePropertyAccessor()
            ));

            // 注册自定义函数
            try {
                evalContext.registerFunction("isEmpty",
                        SqlFunctions.class.getDeclaredMethod("isEmpty", Object.class));
                evalContext.registerFunction("isNotEmpty",
                        SqlFunctions.class.getDeclaredMethod("isNotEmpty", Object.class));
                evalContext.registerFunction("contains",
                        SqlFunctions.class.getDeclaredMethod("contains", Object.class, Object.class));
            } catch (Exception e) {
                log.warn("Failed to register custom functions: {}", e.getMessage());
            }

            // 解析表达式
            Expression expr = expressionCache.get(expression, key -> {
                try {
                    return spelParser.parseExpression(expression);
                } catch (ParseException e) {
                    log.error("Failed to parse SpEL expression: {}", expression, e);
                    throw new SqlProcessingException("Invalid SpEL expression: " + expression, e);
                }
            });

            // 评估表达式
            Boolean result = expr.getValue(evalContext, Boolean.class);
            log.debug("SpEL evaluation model for '{}': {}", expression, result);

            return Boolean.TRUE.equals(result);
        } catch (SpelEvaluationException e) {
            log.warn("SpEL evaluation failed for expression: {}. Error: {}", expression, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error evaluating SpEL expression: {}", expression, e);
            return false;
        }
    }

    static class MapAccessor implements PropertyAccessor {
        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class[]{Map.class};
        }

        @Override
        public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
            if (!(target instanceof Map)) return false;

            Map<?, ?> map = (Map<?, ?>) target;

            // 检查直接属性
            if (map.containsKey(name)) return true;

            // 检查嵌套属性 (e.g., "request.name")
            if (name.contains(".")) {
                String[] parts = name.split("\\.");
                Object current = map.get(parts[0]);

                for (int i = 1; i < parts.length && current != null; i++) {
                    if (current instanceof Map) {
                        current = ((Map<?, ?>) current).get(parts[i]);
                    } else {
                        // 尝试通过反射访问属性
                        try {
                            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(current);
                            current = wrapper.getPropertyValue(parts[i]);
                        } catch (Exception e) {
                            current = null;
                        }
                    }
                }
                return current != null;
            }

            return false;
        }

        @Override
        public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
            if (!(target instanceof Map)) {
                throw new AccessException("Target is not a Map");
            }

            Map<?, ?> map = (Map<?, ?>) target;

            // 直接属性
            if (map.containsKey(name)) {
                return new TypedValue(map.get(name));
            }

            // 嵌套属性
            if (name.contains(".")) {
                String[] parts = name.split("\\.");
                Object current = map.get(parts[0]);

                for (int i = 1; i < parts.length && current != null; i++) {
                    if (current instanceof Map) {
                        current = ((Map<?, ?>) current).get(parts[i]);
                    } else {
                        try {
                            BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(current);
                            current = wrapper.getPropertyValue(parts[i]);
                        } catch (Exception e) {
                            throw new AccessException("Cannot access property '" + parts[i] + "' on object: " + current);
                        }
                    }
                }

                if (current != null) {
                    return new TypedValue(current);
                }
            }

            throw new AccessException("Property '" + name + "' not found");
        }

        @Override
        public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
            return target instanceof Map;
        }

        @Override
        public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {
            if (target instanceof Map) {
                ((Map<String, Object>) target).put(name, newValue);
                return;
            }
            throw new AccessException("Target is not a Map");
        }
    }

    SqlNode parseFragment(String fragment) {
        return parseSqlToAst(fragment);
    }

    private void validateSqlStructure(String sql) {
        if (sql.contains(";")) {
            throw new SqlProcessingException("Multiple SQL statements are not allowed");
        }

        // 添加对 SpEL 表达式的基本验证
        Pattern spelPattern = Pattern.compile("(test\\s*=\\s*['\"])([^'\"\\s]+\\s*!=\\s*[^'\"\\s]+)(['\"])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = spelPattern.matcher(sql);
        while (matcher.find()) {
            String expression = matcher.group(2);
            if (expression.contains("!= ") && !expression.contains("!= ''") && !expression.contains("!= null")) {
                log.warn("Potential incomplete SpEL expression found: {}", expression);
            }
        }

        if (sql.matches("(?i).*\\b(drop|alter|truncate|exec|execute)\\b.*")) {
            throw new SqlProcessingException("Dangerous SQL operation detected: " + sql);
        }
    }

    private void validateParameters(Map<String, Object> params) {
        if (params == null) return;

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            log.debug("Validating parameter: key = {}, value = {}", entry.getKey(), entry.getValue());

            if (entry.getValue() instanceof String str) {
                if (str.contains(";") || str.matches("(?i).*\\b(SELECT|DROP|INSERT|UPDATE|DELETE|EXEC)\\b.*")) {
                    log.error("Invalid parameter value for key: {} with value: {}", entry.getKey(), str);
                    throw new SqlProcessingException("Invalid parameter value for key: " + entry.getKey());
                }
            }

            // 暂时移除对allowedTables的检查，因为SecurityProperties类中没有getAllowedTables()方法
            if ("tableName".equals(entry.getKey())) {
                // 可以在这里添加其他验证逻辑
            }
        }
    }

    private void validateSql(String sql) {
        Pattern safeConcatPattern = Pattern.compile(
                "CONCAT\\s*\\(\\s*(?:'%+'\\s*,\\s*:|:\\w+\\s*,\\s*'%+')(?:\\s*,\\s*'%+')?\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );


        Pattern quotePattern = Pattern.compile("'(?!.*?(?:refid|id)=).*?'");
        Matcher quoteMatcher = quotePattern.matcher(sql);

        while (quoteMatcher.find()) {
            String matched = quoteMatcher.group();
            String content = matched.replace("'", "");

            if (content.contains("%")) {
                Matcher safeMatcher = safeConcatPattern.matcher(sql);
                if (safeMatcher.find() && safeMatcher.regionStart() < quoteMatcher.start()
                        && safeMatcher.regionEnd() > quoteMatcher.end()) {
                    log.debug("Ignoring safe CONCAT pattern: {}", matched);
                    continue;
                }
            }

            if (content.contains("'") || content.contains(";") ||
                    content.matches("(?i).*\\b(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER)\\b.*")) {
                log.warn("Potential SQL injection risk detected, SQL: [{}], risky content: [{}]", sql, matched);
                throw new SqlInjectionRiskException("Potential SQL injection risk: " + matched);
            }
        }

        if (sql.matches("(?i).*\\b(DROP|ALTER|TRUNCATE|EXEC|EXECUTE)\\b.*")) {
            log.warn("Dangerous SQL operation detected: {}", sql);
            throw new SqlInjectionRiskException("Dangerous SQL operation: " + sql);
        }
    }

    public void clearCache() {
        expressionCache.invalidateAll();
        astCache.invalidateAll();
        FragmentCache.clearAll();
        log.info("Dynamic SQL processor caches cleared");
    }

    public CacheStats getExpressionCacheStats() {
        return expressionCache.stats();
    }

    public CacheStats getAstCacheStats() {
        return astCache.stats();
    }

    /**
     * SqlNode接口定义SQL处理节点的基本行为
     */
    interface SqlNode {
        void process(SqlContext context);
    }

    /**
     * SqlNode抽象基类，提供通用功能和模板方法
     */
    abstract static class BaseSqlNode implements SqlNode {
        // 子类共享的日志记录
        protected static final Logger log = LoggerFactory.getLogger(BaseSqlNode.class);
    }

    /**
     * 内容型SQL节点的抽象基类
     */
    abstract static class ContentSqlNode extends BaseSqlNode {
        protected final String content;

        protected ContentSqlNode(String content) {
            this.content = content;
        }

        /**
         * 处理节点内容
         * @param context SQL上下文
         */
        protected void processContent(SqlContext context) {
            if (SqlFunctions.hasText(content)) {
                context.processFragment(content);
            }
        }
    }

    /**
     * 条件型SQL节点的抽象基类
     */
    abstract static class ConditionalSqlNode extends ContentSqlNode {
        protected final String testExpr;

        protected ConditionalSqlNode(String testExpr, String content) {
            super(content);
            this.testExpr = testExpr;
        }

        /**
         * 评估条件表达式
         * @param context SQL上下文
         * @return 表达式评估结果
         */
        protected boolean evaluateCondition(SqlContext context) {
            return SqlFunctions.hasText(testExpr) && context.evaluateSpel(testExpr);
        }
    }

    /**
     * 文本节点，直接输出文本内容
     */
    static class TextNode extends BaseSqlNode {
        private final String text;

        public TextNode(String text) {
            this.text = text;
        }

        @Override
        public void process(SqlContext context) {
            context.append(text);
        }
    }

    /**
     * 条件节点，根据表达式结果决定是否处理内容
     */
    static class IfNode extends ConditionalSqlNode {
        public IfNode(String testExpr, String content) {
            super(testExpr, content);
        }

        @Override
        public void process(SqlContext context) {
            if (evaluateCondition(context)) {
                processContent(context);
            }
        }
    }

    /**
     * Where节点，生成WHERE子句并处理前导的AND/OR关键字
     */
    static class WhereNode extends ContentSqlNode {
        public WhereNode(String content) {
            super(content);
        }

        @Override
        public void process(SqlContext context) {
            context.pushClosure(sql -> {
                String trimmed = sql.trim();
                if (SqlFunctions.hasText(trimmed)) {
                    trimmed = trimmed.replaceAll("(?i)^\\s*(AND|OR)\\s+", "");
                    return "WHERE " + trimmed;
                }
                return "";
            });
            context.startTagContent();
            processContent(context);
            context.closeTag("where");
        }
    }

    static class ForeachNode implements SqlNode {
        private final String collection;
        private final String item;
        private final String index;
        private final String open;
        private final String separator;
        private final String close;
        private final String content;

        public ForeachNode(String collection, String item, String index,
                           String open, String separator, String close, String content) {
            this.collection = collection;
            this.item = item;
            this.index = index;
            this.open = open;
            this.separator = separator;
            this.close = close;
            this.content = content;
        }

        @Override
        public void process(SqlContext context) {
            if (collection == null) return;
            Object collectionObj = context.getParams().get(collection);
            if (!(collectionObj instanceof Iterable<?> iterable)) {
                log.warn("Collection {} is not iterable", collection);
                return;
            }

            List<String> parts = new ArrayList<>();
            int idx = 0;
            for (Object element : iterable) {
                if (element == null) continue;
                String itemParamName = item + "_" + idx;
                context.addParam(itemParamName, element);
                String itemContent = content.replace("#{" + item + "}", "#{" + itemParamName + "}");
                if (index != null) {
                    itemContent = itemContent.replace("#{" + index + "}", String.valueOf(idx));
                }
                parts.add(itemContent);
                idx++;
            }

            if (!parts.isEmpty()) {
                String result = open + String.join(separator, parts) + close;
                context.append(result);
            }
        }
    }

    /**
     * Choose节点，作为when和otherwise节点的容器
     */
    static class ChooseNode extends ContentSqlNode {
        public ChooseNode(String content) {
            super(content);
        }

        @Override
        public void process(SqlContext context) {
            processContent(context);
        }
    }

    /**
     * When节点，在choose结构中表示条件分支
     */
    static class WhenNode extends ConditionalSqlNode {
        public WhenNode(String testExpr, String content) {
            super(testExpr, content);
        }

        @Override
        public void process(SqlContext context) {
            if (evaluateCondition(context)) {
                processContent(context);
                context.getParams().put("_choose_matched", true);
            }
        }
    }

    /**
     * Otherwise节点，在choose结构中表示默认分支
     */
    static class OtherwiseNode extends ContentSqlNode {
        public OtherwiseNode(String content) {
            super(content);
        }

        @Override
        public void process(SqlContext context) {
            if (!Boolean.TRUE.equals(context.getParams().get("_choose_matched"))) {
                processContent(context);
            }
        }
    }

    /**
     * Set节点，生成SET子句并处理末尾的逗号
     */
    static class SetNode extends ContentSqlNode {
        public SetNode(String content) {
            super(content);
        }

        @Override
        public void process(SqlContext context) {
            context.pushClosure(sql -> {
                String trimmed = sql.trim();
                if (SqlFunctions.hasText(trimmed)) {
                    trimmed = trimmed.replaceAll(",\\s*$", "");
                    return "SET " + trimmed;
                }
                return "";
            });
            context.startTagContent();
            processContent(context);
            context.closeTag("set");
        }
    }

    /**
     * Trim节点，用于灵活地定制SQL片段的前缀和后缀，并移除指定的前缀和后缀内容
     */
    static class TrimNode extends ContentSqlNode {
        private final String prefix;
        private final String suffix;
        private final String prefixOverrides;
        private final String suffixOverrides;

        public TrimNode(String prefix, String suffix,
                        String prefixOverrides, String suffixOverrides, String content) {
            super(content);
            this.prefix = prefix;
            this.suffix = suffix;
            this.prefixOverrides = prefixOverrides;
            this.suffixOverrides = suffixOverrides;
        }

        @Override
        public void process(SqlContext context) {
            context.pushClosure(sql -> {
                String trimmed = sql.trim();
                if (SqlFunctions.hasText(prefixOverrides)) {
                    for (String override : prefixOverrides.split("\\|")) {
                        String pattern = "(?i)^" + Pattern.quote(override.trim());
                        trimmed = trimmed.replaceAll(pattern, "");
                    }
                }
                if (SqlFunctions.hasText(suffixOverrides)) {
                    for (String override : suffixOverrides.split("\\|")) {
                        String pattern = "(?i)" + Pattern.quote(override.trim()) + "$";
                        trimmed = trimmed.replaceAll(pattern, "");
                    }
                }
                trimmed = trimmed.trim();
                if (SqlFunctions.hasText(trimmed)) {
                    return (SqlFunctions.hasText(prefix) ? prefix + " " : "") +
                            trimmed +
                            (SqlFunctions.hasText(suffix) ? " " + suffix : "");
                }
                return "";
            });
            context.startTagContent();
            processContent(context);
            context.closeTag("trim");
        }
    }

    /**
     * Bind节点，用于将表达式结果绑定到参数中
     */
    static class BindNode extends ContentSqlNode {
        private final String name;
        private final String valueExpr;

        public BindNode(String name, String valueExpr, String content) {
            super(content);
            this.name = name;
            this.valueExpr = valueExpr;
        }

        @Override
        public void process(SqlContext context) {
            Object value = context.evaluateExpression(valueExpr);
            context.getParams().put(name, value);
            processContent(context);
        }
    }

    /**
     * 组合节点，包含并处理多个子节点
     */
    static class CompositeNode extends BaseSqlNode {
        private final List<SqlNode> nodes;

        public CompositeNode(List<SqlNode> nodes) {
            this.nodes = nodes;
        }

        @Override
        public void process(SqlContext context) {
            for (SqlNode node : nodes) {
                node.process(context);
            }
        }
    }

    static class SqlContext extends AbstractSqlContext<SqlContext.SqlClosure> {
        private static final Pattern ALLOWED_DOLLAR_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+");

        private final MyBatisSqlProcessor processor;
        private final SqlConfigProperties properties;

        public SqlContext(MyBatisSqlProcessor processor, SqlConfigProperties properties, Map<String, Object> params) {
            super(params);
            this.processor = processor;
            this.properties = properties;
        }

        public void closeTag(String tagName) {
            if (closures.isEmpty()) {
                log.warn("No closure to pop for tag: {}", tagName);
                return;
            }
            SqlClosure closure = closures.pop();
            String content = isProcessingTagContent ? currentTagContent.toString() : sql.toString();
            if (isProcessingTagContent) {
                isProcessingTagContent = false;
                currentTagContent.setLength(0);
            } else {
                sql.setLength(0);
            }
            sql.append(closure.apply(content));
        }

        public void pushClosure(SqlClosure closure) {
            closures.push(closure);
        }

        public void addParam(String name, Object value) {
            if (value != null) params.put(name, value);
        }

        public boolean evaluateSpel(String expression) {
            return processor.evaluateSpel(expression, params);
        }

        public Object evaluateExpression(String expression) {
            try {
                Expression expr = processor.spelParser.parseExpression(expression);
                StandardEvaluationContext evalContext = new StandardEvaluationContext();

                // Set the root object to the params map
                evalContext.setRootObject(params);

                // Set property accessors to handle nested properties
                evalContext.setPropertyAccessors(Arrays.asList(
                        new MapAccessor(),
                        new ReflectivePropertyAccessor()
                ));

                return expr.getValue(evalContext);
            } catch (Exception e) {
                log.warn("Expression evaluation failed: {}", expression, e);
                return null;
            }
        }

        public void processFragment(String fragment) {
            SqlNode ast = processor.parseFragment(fragment);
            ast.process(this);
        }

        // In SqlContext class, modify getSql() method
        public String getSql() {
            String rawSql = sql.toString().replaceAll("\\s+", " ").trim();
            StringBuilder result = new StringBuilder();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawSql);

            while (matcher.find()) {
                String hashParam = matcher.group(1);
                String dollarParam = matcher.group(2);

                if (hashParam != null) {
                    // Handle nested properties using SpEL
                    Object value = evaluateExpression(hashParam);
                    if (value == null) {
                        throw new SqlProcessingException("#{" + hashParam + "} is null");
                    }
                    //String paramName = "param_" + hashParam.replace('.', '_');
                    addParam(hashParam, value);
                   matcher.appendReplacement(result, ":" + hashParam);
                } else if (dollarParam != null) {
                    Object value = evaluateExpression(dollarParam);
                    if (value == null) {
                        throw new SqlProcessingException("${" + dollarParam + "} is null");
                    }
                    String valueStr = value.toString();
                    if (!ALLOWED_DOLLAR_PATTERN.matcher(valueStr).matches()) {
                        throw new SqlInjectionRiskException("Invalid ${" + dollarParam + "} value: " + valueStr);
                    }
                    matcher.appendReplacement(result, Matcher.quoteReplacement(valueStr));
                }
            }

            matcher.appendTail(result);
            return result.toString().replaceAll("\\s+", " ").trim();
        }

        public Map<String, Object> getParams() {
            return Collections.unmodifiableMap(params);
        }

        interface SqlClosure {
            String apply(String content);
        }
    }

    // SqlFunctions已替换为统一的StringUtils工具类
}