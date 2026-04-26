package com.bone.tpa.push.util;

import com.alibaba.fastjson.JSONObject;
import com.bone.tpa.facade.vo.OptionSetDTO;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

public class OptionSetUtil {

    public static void getOptionSetValue(OptionSetDTO outOptionSet, String methodName, Object o, String columnName) {
        if (Objects.isNull(outOptionSet)) {
            return;
        }
        String extendStr = outOptionSet.getExtraProperty();
        if (StringUtils.isBlank(extendStr)) {
            return;
        }
        JSONObject extObj = JSONObject.parseObject(extendStr);
        String code2 = extObj.getString(columnName);
        if (StringUtils.isBlank(code2)) {
            return;
        }
        try {
            Method method = Arrays.stream(o.getClass().getMethods())
                    .filter(m -> m.getName().equals(methodName) && m.getParameterCount() == 1)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("未找到方法: " + methodName));

            Class<?> paramType = method.getParameterTypes()[0];

            Object arg = convertType(code2, paramType);

            method.invoke(o, arg);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        }
    }

    private static Object convertType(String value, Class<?> targetType) {
        if (targetType == String.class) return value;
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(value);
        if (targetType == BigDecimal.class) return new BigDecimal(value);
        return value;
    }
}
