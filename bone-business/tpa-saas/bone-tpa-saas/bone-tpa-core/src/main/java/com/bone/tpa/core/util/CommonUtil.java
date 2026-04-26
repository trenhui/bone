package com.bone.tpa.core.util;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
public class CommonUtil {


    private static final List<SimpleDateFormat> formatLists = Arrays.asList(
            new SimpleDateFormat("yyyy-MM-dd"),
            new SimpleDateFormat("yyyy年MM月dd日"),
            new SimpleDateFormat("yyyy/MM/dd"),
            new SimpleDateFormat("yyyyMMdd"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm"),
            new SimpleDateFormat("yyyy-MM-dd HH"),
            new SimpleDateFormat("yyyy-MM"),
            new SimpleDateFormat("yyyy")
    );

    public static Date parseStrToDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        for (SimpleDateFormat format : formatLists) {
            try {
                return format.parse(dateStr);
            } catch (ParseException e) {
                log.info("日期转换异常, dateStr:{}, format:{}", dateStr, format.toPattern());
            }
        }
        return null;
    }
}
