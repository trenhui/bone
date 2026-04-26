package com.bone.lowcode.infra.domain.util;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonUtil {

    public static String convertToCamelCase(String str) {
        // 将字符串全部转为小写并拆分为单词数组
        String[] parts = str.toLowerCase().split("_");

        // 初始化 StringBuilder，用于拼接结果
        StringBuilder camelCaseString = new StringBuilder(parts[0]);

        // 遍历单词数组，从第二个单词开始将首字母大写并拼接
        for (int i = 1; i < parts.length; i++) {
            camelCaseString.append(parts[i].substring(0, 1).toUpperCase())
                    .append(parts[i].substring(1));
        }

        return camelCaseString.toString();
    }

    /**
     * 返回字符串中第一个匹配正则的子串
     */
    public static String findFirstMatch(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
