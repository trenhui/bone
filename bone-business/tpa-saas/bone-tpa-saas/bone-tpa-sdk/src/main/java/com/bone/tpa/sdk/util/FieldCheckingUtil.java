package com.bone.tpa.sdk.util;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FieldCheckingUtil {


    public static List<String> getNullFieldNames(Object obj) {
        if (obj == null) {
            return List.of();
        }

        return Arrays.stream(obj.getClass().getDeclaredFields())
                .filter(field -> {
                    try {
                        field.setAccessible(true);
                        return field.get(obj) == null;
                    } catch (IllegalAccessException e) {
                        return false;
                    }
                })
                .map(Field::getName)
                .collect(Collectors.toList());
    }
}
