//package com.bone.engine.extension.converter;
//
//import com.fasterxml.jackson.core.JsonPointer;
//import com.fasterxml.jackson.databind.JsonNode;
//import jakarta.servlet.http.HttpServletRequest;
//import org.reflections.Reflections;
//import org.reflections.scanners.Scanners;
//import org.reflections.util.ClasspathHelper;
//import org.reflections.util.ConfigurationBuilder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpInputMessage;
//import org.springframework.http.converter.HttpMessageConversionException;
//import org.springframework.http.converter.HttpMessageNotReadableException;
//import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
//import org.springframework.util.StreamUtils;
//import org.springframework.util.StringUtils;
//
//import java.io.IOException;
//import java.lang.reflect.Modifier;
//import java.lang.reflect.ParameterizedType;
//import java.lang.reflect.Type;
//import java.nio.charset.StandardCharsets;
//import java.util.Optional;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.stream.Collectors;
//
///**
// * Custom DTO Mapping Jackson2 HttpMessage Converter
// * <p>
// * Handles dynamic DTO deserialization based on tenant codes and caching for improved performance.
// */
//public class CustomDtoMappingJackson2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
//
//    private static final Logger logger = LoggerFactory.getLogger(CustomDtoMappingJackson2HttpMessageConverter.class);
//
//    private final HttpServletRequest request;
//    private final ConcurrentMap<String, Set<Class<?>>> subTypeCache = new ConcurrentHashMap<>();
//    private final ConcurrentMap<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
//
//    public CustomDtoMappingJackson2HttpMessageConverter(HttpServletRequest request) {
//        super();
//        this.request = request;
//    }
//
//    @Override
//    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
//        logger.debug("Executing read method for type: {}", type.getTypeName());
//
//        // 读取请求体内容
//        String body = StreamUtils.copyToString(inputMessage.getBody(), StandardCharsets.UTF_8);
//
//        // 检查并清理 JSON 数据
//        String processedBody = cleanJsonBody(body);
//
//        // 获取租户代码
//        String tenantCode = Optional.ofNullable(request.getParameter("tenantCode"))
//                .orElseGet(() -> extractTenantCodeFromJson(processedBody));
//        logger.debug("Extracted tenantCode: {}", tenantCode);
//
//        // 确定目标类
//        Class<?> clazz = resolveTargetClass(type, tenantCode, inputMessage);
//
//        // 反序列化 JSON 到目标类
//        try {
//            return this.getObjectMapper().readValue(processedBody, clazz);
//        } catch (IOException e) {
//            logger.error("Error parsing JSON to target class: {}", clazz.getName(), e);
//            throw new HttpMessageNotReadableException("Failed to read HTTP message for class: " + clazz.getName(), e, inputMessage);
//        }
//    }
//
//    private Class<?> resolveTargetClass(Type type, String tenantCode, HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
//        if (type instanceof Class<?>) {
//            // 如果是普通类，直接返回
//            return (Class<?>) type;
//        } else if (type instanceof ParameterizedType) {
//            // 如果是泛型类型，获取其原始类型
//            ParameterizedType parameterizedType = (ParameterizedType) type;
//            Type rawType = parameterizedType.getRawType();
//
//            if (rawType instanceof Class<?>) {
//                return (Class<?>) rawType;
//            } else {
//                throw new HttpMessageNotReadableException("Unsupported raw type: " + rawType, inputMessage);
//            }
//        } else {
//            // 如果既不是 Class<?> 也不是 ParameterizedType，抛出异常
//            throw new HttpMessageNotReadableException("Unsupported type: " + type, inputMessage);
//        }
//    }
//
//    private String cleanJsonBody(String body) {
//        if (body.startsWith("\"") && body.endsWith("\"")) {
//            logger.warn("JSON body is wrapped as a string, attempting to clean it.");
//            return body.substring(1, body.length() - 1).replace("\\\"", "\"");
//        }
//        return body;
//    }
//
//
//
//    /**
//     * 从 JSON 中提取指定字段的值
//     */
//    private String extractFieldFromJson(String json, String fieldName) {
//        try {
//            JsonPointer pointer = JsonPointer.compile("/" + fieldName);
//            JsonNode rootNode = this.getObjectMapper().readTree(json);
//            JsonNode targetNode = rootNode.at(pointer);
//
//            if (targetNode.isMissingNode() || targetNode.isNull()) {
//                logger.warn("Field '{}' not found or is null in JSON: {}", fieldName, json);
//                return null;
//            }
//            return targetNode.asText();
//        } catch (IOException e) {
//            logger.error("Error extracting field '{}' from JSON: {}", fieldName, json, e);
//            throw new HttpMessageConversionException("Failed to extract field '" + fieldName + "' from JSON", e);
//        }
//    }
//
//    /**
//     * 从 JSON 中提取 tenantCode 字段的值
//     */
//    private String extractTenantCodeFromJson(String json) {
//        return extractFieldFromJson(json, "tenantCode");
//    }
//
//    /**
//     * 根据 tenantCode 动态解析目标类
//     */
//    private Class<?> resolveTargetClass(String tenantCode, Type type) {
//        if (!(type instanceof Class<?>)) {
//            throw new IllegalArgumentException("Unsupported type: " + type);
//        }
//
//        Class<?> baseClass = (Class<?>) type;
//
//        if (!StringUtils.hasText(tenantCode)) {
//            logger.debug("No tenantCode provided, using base class: {}", baseClass.getName());
//            return baseClass;
//        }
//
//        String cacheKey = tenantCode + "_" + baseClass.getName();
//        return cachedClasses.computeIfAbsent(cacheKey, key -> {
//            logger.debug("Resolving target class for tenantCode: {}, baseClass: {}", tenantCode, baseClass.getName());
//            Set<Class<?>> subTypes = loadSubTypes(baseClass);
//
//            // 过滤匹配的子类
//            Set<Class<?>> matchingSubTypes = subTypes.stream().filter(subClass -> subClass.getSimpleName().startsWith(tenantCode) && !Modifier.isAbstract(subClass.getModifiers())).collect(Collectors.toSet());
//
//            if (matchingSubTypes.size() > 1) {
//                throw new IllegalStateException("Multiple matching classes found for tenantCode: " + tenantCode);
//            } else if (matchingSubTypes.isEmpty()) {
//                logger.warn("No matching subclasses found for tenantCode: {}, using base class: {}", tenantCode, baseClass.getName());
//                return baseClass;
//            }
//
//            return matchingSubTypes.iterator().next();
//        });
//    }
//
//    /**
//     * 加载指定类的所有子类
//     */
//    private Set<Class<?>> loadSubTypes(Class<?> baseClass) {
//        return subTypeCache.computeIfAbsent(baseClass.getName(), key -> {
//            String basePackage = baseClass.getPackage().getName().replace(".application.dto", ".infrastructure.ext");
//            logger.debug("Scanning package: {} for subclasses of {}", basePackage, baseClass.getName());
//
//            Reflections reflections = new Reflections(new ConfigurationBuilder().setUrls(ClasspathHelper.forPackage(basePackage)).setScanners(Scanners.SubTypes));
//            return reflections.getSubTypesOf((Class<Object>) baseClass);
//        });
//    }
//}