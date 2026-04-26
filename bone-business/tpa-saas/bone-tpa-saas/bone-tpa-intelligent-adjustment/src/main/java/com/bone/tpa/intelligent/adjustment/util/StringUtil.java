package com.bone.tpa.intelligent.adjustment.util;

import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字符串工具类
 */
public class StringUtil {

    public static final String planNameMatcher = "[\\u4e00-\\u9fa5a-zA-Z0-9_]{2,20}";

    public static final String planCodeMatcher = "[a-zA-Z0-9_]*";



    public static final String coverageNameMatcher = "[\\u4e00-\\u9fa5a-zA-Z0-9_]{2,20}";

    public static final String coverageCodeMatcher = "[a-zA-Z0-9_]*";


    public static void main(String[] args){
        System.out.println("planName test");
        System.out.println("123abc".matches(planNameMatcher));
        System.out.println("123_abc".matches(planNameMatcher));
        System.out.println("123测试11".matches(planNameMatcher));
        System.out.println("123_测试11".matches(planNameMatcher));
        System.out.println("123 测试11".matches(planNameMatcher));
        System.out.println("123测试11123测试11123测试11123测试11".matches(planNameMatcher));
        System.out.println("planCode test");
        System.out.println("123".matches(planCodeMatcher));
        System.out.println("123abc".matches(planCodeMatcher));
        System.out.println("123_abc".matches(planCodeMatcher));
        System.out.println("123_abc!".matches(planCodeMatcher));
        System.out.println("123 abc".matches(planCodeMatcher));
        System.out.println("123测试11".matches(planCodeMatcher));
        System.out.println("123_测试11".matches(planCodeMatcher));
        System.out.println("123 测试11".matches(planCodeMatcher));




    }

    /**
     * 进行如下的字符串判断，任意一个满足返回true
     * 1. 为空
     * 2. 为空字符串
     * 3. 含有空格，各种意义上的
     * 4. 只含有特殊字符
     *
     * @param obj
     * @return
     */
    public static Boolean stringInvalid(String obj, String name) {
        if (obj == null || obj.isEmpty()) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, name + "不能为空");
        }
        if (obj.matches(".*\\s.*")) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, name + "不能仅含空格");
        }
        if (obj.matches("^[\\p{Punct}\\p{S}]+$")) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, name + "不能仅含特殊字符");
        }

        return true;
    }


    public static Boolean stringRegexCheck(String obj,String matcherRegx, String errorInfo) {
        if (obj == null ) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, errorInfo);
        }
        if (!obj.matches(matcherRegx)) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR,errorInfo);
        }

        return true;
    }


    public static String mapToRows(Map<String, BigDecimal> quotaMap) {
        List<String> resultList = new ArrayList<>();

        for (String key : quotaMap.keySet()) {
            String result = key + ": " + quotaMap.get(key);
            resultList.add(result);
        }

        return String.join("\n", resultList);
    }

    public static Map<String, BigDecimal> rowsToMap(String rowsString) {
        Map<String, BigDecimal> resultMap = new HashMap<>();

        if (rowsString == null || rowsString.trim().isEmpty()) {
            return resultMap;
        }

        // 按换行符分割字符串
        String[] lines = rowsString.split("\n");

        for (String line : lines) {
            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

            // 查找第一个冒号的位置
            int colonIndex = line.indexOf(":");
            if (colonIndex == -1) {
                // 如果没有冒号，跳过这一行或根据需求处理
                continue;
            }

            // 提取键和值
            String key = line.substring(0, colonIndex).trim();
            String valueStr = line.substring(colonIndex + 1).trim();

            try {
                // 将字符串转换为BigDecimal
                BigDecimal value = new BigDecimal(valueStr);
                resultMap.put(key, value);
            } catch (NumberFormatException e) {
                // 如果转换失败，跳过这一行或根据需求处理
                System.err.println("无法解析数字: " + valueStr);
            }
        }

        return resultMap;
    }
}
