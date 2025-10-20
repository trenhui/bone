package com.bone.metadata.sdk.query.dsl;

import java.util.regex.Pattern;

/**
 * 定义正则表达式模式常量
 */
public class PatternConstants {
    /**
     * 用于匹配方法引用的正则表达式
     */
    public static final Pattern METHOD_REFERENCE_PATTERN = Pattern.compile("get(\\p{javaUpperCase}[\\w\\$]+)");
    
    /**
     * 用于匹配Lambda表达式的正则表达式
     */
    public static final Pattern LAMBDA_EXPRESSION_PATTERN = Pattern.compile("[\\w\\$](\\d+)\\.([a-zA-Z0-9_]+)");
    
    /**
     * 用于匹配IS NULL条件的正则表达式
     */
    public static final Pattern IS_NULL_PATTERN = Pattern.compile("^([\\w\\$]+)\\s+IS\\s+NULL$", Pattern.CASE_INSENSITIVE);
    
    /**
     * 用于匹配IS NOT NULL条件的正则表达式
     */
    public static final Pattern IS_NOT_NULL_PATTERN = Pattern.compile("^([\\w\\$]+)\\s+IS\\s+NOT\\s+NULL$", Pattern.CASE_INSENSITIVE);
    
    /**
     * 用于字符串匹配的正则表达式模式
     */
    public static final String STRING_METHOD_REFERENCE_PATTERN = "(?:[\\w\\$]+\\.)*[\\w\\$]+::[\\w\\$]+";
    
    /**
     * 用于字符串匹配的Lambda表达式模式
     */
    public static final String STRING_LAMBDA_EXPRESSION_PATTERN = "\\([^)]*\\)\\s*->\\s*[^;]+";
}