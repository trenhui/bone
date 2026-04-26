package com.bone.tpa.sdk.util;


import com.bone.tpa.sdk.adjustment.enums.DateRangeEnum;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 日期工具类
 * 功能：
 * 1. 判断两个Date是否是同一天
 * 2. 计算两个Date之间的天数差（支持三种模式）
 * 3. 判断某个日期是否在两个日期之间
 */
@Slf4j
public class DateUtil {

    /**
     * 判断两个Date是否是同一天
     * @param date1 第一个日期（非空）
     * @param date2 第二个日期（非空）
     * @return 是否同一天
     * @throws IllegalArgumentException 如果任一参数为null
     */
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null) {
            return false;
        }

        LocalDate localDate1 = toLocalDate(date1);
        LocalDate localDate2 = toLocalDate(date2);
        return localDate1.isEqual(localDate2);
    }


    /**
     * 判断两个Date是否是同一月
     * @param date1 第一个日期（非空）
     * @param date2 第二个日期（非空）
     * @return 是否同一月
     * @throws IllegalArgumentException 如果任一参数为null
     */
    public static boolean isSameMonth(Date date1, Date date2) {
        if (date1 == null) {
            return false;
        }

        LocalDate localDate1 = toLocalDate(date1);
        LocalDate localDate2 = toLocalDate(date2);

        return localDate1.getYear() == localDate2.getYear()
                && localDate1.getMonth() == localDate2.getMonth();
    }


    /**
     * 计算两个日期的天数差
     * @param start 开始日期（非空）
     * @param end 结束日期（非空）
     * @param mode 计算模式（非空）
     * @return 天数差（始终返回非负数）
     * @throws IllegalArgumentException 如果任一参数为null
     */
    public static long calculateDayDifference(Date start, Date end, DateRangeEnum mode) {
        validateDatesAndMode(start, end, mode);
        switch (mode) {
            case NATURAL: return calculateInclusiveDays(start, end);
            case CALENDER: return calculateExclusiveDays(start, end);
            case TWENTY_FOUR_HOUR: return calculate24HourDays(start, end);
            default: throw new AssertionError("Unreachable code");
        }
    }


    /**
     * 获取这个期间的最早的日期
     * @param start
     * @param end
     * @param dateList
     * @return
     */
    public static String getFirstDayInPeriodString(String start, String end, List<String> dateList) {
        SimpleDateFormat returnFormat = new SimpleDateFormat("yyyy-MM-dd");

        return returnFormat.format(getFirstDayInPeriod(start, end, dateList));
    }


    /**
     * 获取这个期间的最早的日期
     * @param start
     * @param end
     * @param dateList
     * @return
     */
    public static Date getFirstDayInPeriod(String start, String end, List<String> dateList) {
        Date exactDate = null;
        try {
            Date startDate = parseDate(start);
            Date endDate = parseDate(end);

            exactDate = endDate;
            for (String dateString : dateList) {
                Date date = parseDate(dateString);

                if (isInPeriod(startDate, endDate, date)) {
                    if (date.before(exactDate)) {
                        exactDate = date;
                    }
                }
            }
        } catch (ParseException e) {
            log.error("计算出险日期错误" + e.getMessage());
            throw new RuntimeException(e);
        }

        return exactDate;
    }

    /**
     * 解析日期字符串
     * @param dateString 日期字符串
     * @return 解析后的日期
     * @throws ParseException 解析异常
     */
    private static Date parseDate(String dateString) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.parse(dateString);
    }


    public static boolean isInPeriod(String start, String end, Date object) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

        Date exactDate = null;
        try {
            Date startDate = dateFormat.parse(start);
            Date endDate = dateFormat.parse(end);

            return isInPeriod(startDate, endDate, object);
        } catch (ParseException e) {
            log.error("计算出险日期错误" + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查object是否在start和end之间
     *
     * @param start
     * @param end
     * @param object
     * @return
     */
    public static boolean isInPeriod(Date start, Date end, Date object) {
        // 处理无效输入：目标日期为 null 直接返回 false
        if (object == null) {
            return false;
        }

        // 处理时间段无效的情况：start 和 end 都不为 null，但 start 在 end 之后
        if (start != null && end != null && start.after(end)) {
            return false;
        }

        // 检查目标日期是否在起始日期之后（含等于）
        boolean isAfterStart = (start == null) || !object.before(start);

        // 检查目标日期是否在结束日期之前（含等于）
        boolean isBeforeEnd = (end == null) || !object.after(end);

        return isAfterStart && isBeforeEnd;
    }



    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static long calculateInclusiveDays(Date start, Date end) {
        LocalDate startDate = toLocalDate(start);
        LocalDate endDate = toLocalDate(end);
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private static long calculateExclusiveDays(Date start, Date end) {
        return Math.abs(ChronoUnit.DAYS.between(toLocalDate(start), toLocalDate(end)));
    }

    private static long calculate24HourDays(Date start, Date end) {
        long diffMillis = Math.abs(end.getTime() - start.getTime());
        return diffMillis / (24 * 60 * 60 * 1000);
    }

    private static void validateDates(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("Dates cannot be null");
        }
    }

    private static void validateDatesAndMode(Date start, Date end, DateRangeEnum mode) {
        validateDates(start, end);
        Objects.requireNonNull(mode, "Mode cannot be null");
    }


    public static BigDecimal getDifferenceInHours(Date start, Date end) {
        Duration duration = Duration.between(start.toInstant(), end.toInstant());
        return new BigDecimal(duration.toNanos())
                .divide(BigDecimal.valueOf(3_600_000_000_000L), 15, RoundingMode.HALF_UP);
    }

}
