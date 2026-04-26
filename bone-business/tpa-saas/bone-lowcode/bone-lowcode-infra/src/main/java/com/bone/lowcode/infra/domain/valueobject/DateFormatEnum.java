package com.bone.lowcode.infra.domain.valueobject;


import lombok.Getter;
import lombok.ToString;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

//时间格式枚举
@Getter
@ToString
public enum DateFormatEnum {

    Y(1, "yyyy", "^\\d{4}$"),
    YM(2, "yyyy-MM", "^\\d{4}-(0[1-9]|1[0-2])$"),
    YMD(3, "yyyy-MM-dd", "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$"),
    YMDHM(4, "yyyy-MM-dd HH:mm", "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$"),
    YMDHMS(5, "yyyy-MM-dd HH:mm:ss", "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$"),
    ;

    private final int code;
    private final String desc;
    private final String pattern;

    DateFormatEnum(int code, String desc, String pattern) {
        this.code = code;
        this.desc = desc;
        this.pattern = pattern;
    }

    public static String getDateFormatByCode(int code) {
        for (DateFormatEnum item : DateFormatEnum.values()) {
            if (item.getCode() == code) {
                return item.getDesc();
            }
        }
        return null;
    }

    public static Date parseStrToDate(String str) throws ParseException {
        for (DateFormatEnum item : DateFormatEnum.values()) {
            if (str.matches(item.getPattern())) {
                SimpleDateFormat sdf = new SimpleDateFormat(item.getDesc());
                return sdf.parse(str);
            }
        }
        return null;
    }

    public static String parseDateToStr(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(YMDHMS.getDesc());
        return sdf.format(date);
    }
}
