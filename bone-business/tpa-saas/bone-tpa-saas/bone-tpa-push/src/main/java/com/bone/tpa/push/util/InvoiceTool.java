package com.bone.tpa.push.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ArrayUtil;
import jodd.util.ArraysUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @Author feihaiming
 * @create 2025/10/21 19:29
 */
public class InvoiceTool {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String getMinHospitalDate(List<String> hospitalPeriods) {
        LocalDate localDate = hospitalPeriods.stream()
                .filter(period -> period != null && !period.isEmpty())
                .map(period -> {
                    try {
                        // 按逗号分割，取第一个日期
                        String[] parts = period.split(",");
                        if (parts.length == 0) {
                            return null;
                        }

                        String dateStr = parts[0].trim();

                        // 如果包含时间部分，只取日期部分
                        if (dateStr.contains(" ")) {
                            dateStr = dateStr.substring(0, dateStr.indexOf(' '));
                        }

                        return LocalDate.parse(dateStr, DATE_FORMATTER);
                    } catch (Exception e) {
                        // 解析失败返回 null
                        return null;
                    }
                })
                .filter(Objects::nonNull) // 过滤掉解析失败产生的 null
                .min(Comparator.naturalOrder()) // 现在流中没有 null，可以安全使用 naturalOrder
                .orElse(null);

        if (localDate != null) {
            return DateUtil.formatDate(toDate(localDate));
        }
        return null;
    }

    public static String getMaxHospitalDate(List<String> hospitalPeriods) {
        LocalDate localDate = hospitalPeriods.stream()
                .filter(period -> period != null && !period.isEmpty())
                .map(period -> {
                    try {
                        // 按逗号分割，取第一个日期
                        String[] parts = period.split(",");
                        if (parts.length < 2) {
                            return null;
                        }

                        String dateStr = parts[1].trim();

                        // 如果包含时间部分，只取日期部分
                        if (dateStr.contains(" ")) {
                            dateStr = dateStr.substring(0, dateStr.indexOf(' '));
                        }

                        return LocalDate.parse(dateStr, DATE_FORMATTER);
                    } catch (Exception e) {
                        // 解析失败返回 null
                        return null;
                    }
                })
                .filter(Objects::nonNull) // 过滤掉解析失败产生的 null
                .min(Comparator.naturalOrder()) // 现在流中没有 null，可以安全使用 naturalOrder
                .orElse(null);

        if (localDate != null) {
            return DateUtil.formatDate(toDate(localDate));
        }
        return null;
    }

    public static String getFirstLiabilityUUID(String liabilityUUID) {
        if (StringUtils.isBlank(liabilityUUID)) {
            return null;
        }
        String[] liabilityUUIDs = StringUtils.split(liabilityUUID, ",");
        if (ArrayUtils.isEmpty(liabilityUUIDs)) {
            return null;
        }
        String str = Arrays.asList(liabilityUUIDs).stream()
                .filter(uuid -> StringUtils.isNotBlank(uuid))
                .findFirst()
                .orElse(null);
        return str;
    }

    public static Date toDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
