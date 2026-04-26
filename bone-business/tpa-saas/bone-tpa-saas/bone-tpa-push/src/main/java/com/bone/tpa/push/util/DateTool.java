package com.bone.tpa.push.util;

import cn.hutool.core.date.DateUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

/**
 * @Author feihaiming
 * @create 2025/10/22 16:11
 */
public class DateTool {
    public static Date getFirstDateTime(String datePeriod){
        if (StringUtils.isBlank(datePeriod)) {
            return null;
        }
        String[] split = datePeriod.split(",");
        if (split.length == 0) {
            return null;
        }
        return DateUtil.parseDateTime(split[0]);
    }

    public static String getFirstDateTimeStr(String datePeriod){
        Date firstDateTime = getFirstDateTime(datePeriod);
        return DateUtil.formatDateTime(firstDateTime);
    }

    public static Date getLastDateTime(String datePeriod){
        if (StringUtils.isBlank(datePeriod)) {
            return null;
        }
        String[] split = datePeriod.split(",");
        if (split.length < 2) {
            return null;
        }
        return DateUtil.parseDateTime(split[1]);
    }

    public static String getLastDateTimeStr(String datePeriod){
        Date lastDateTime = getLastDateTime(datePeriod);
        return DateUtil.formatDateTime(lastDateTime);
    }

    public static Date getFirstDate(String datePeriod){
        if (StringUtils.isBlank(datePeriod)) {
            return null;
        }
        String[] split = datePeriod.split(",");
        if (split.length == 0) {
            return null;
        }
        return DateUtil.parseDate(split[0]);
    }

    public static String getFirstDateStr(String datePeriod){
        Date firstDate = getFirstDate(datePeriod);
        return DateUtil.formatDate(firstDate);
    }

    public static Date getLastDate(String datePeriod){
        if (StringUtils.isBlank(datePeriod)) {
            return null;
        }
        String[] split = datePeriod.split(",");
        if (split.length < 2) {
            return null;
        }
        return DateUtil.parseDate(split[1]);
    }

    public static String getLastDateStr(String datePeriod){
        Date lastDate = getLastDate(datePeriod);
        return DateUtil.formatDate(lastDate);
    }
}
