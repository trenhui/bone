package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 直付证件类型
 * 取自TPA
 */
@Getter
public enum ZFCertTypeEnum {

    SFZ("身份证", 0, 0),
    JRZ("军人证", 1, 1),
    ZGHZ("中国护照", 2, 2),
    CSZ("出生证", 3, 3),
    YCSFZ("异常身份证", 4, 4),
    GAHXZ("港澳回乡证", 5, 5),
    HKB("户口本", 6, 11),
    JCZ("警察证", 7, 14),
    TBZ("台胞证", 8, 9),
    WGRYJJLSFZ("外国人永久居留身份证", 9, 8),
    GAJMJZZ("港澳居民居住证", 10, 12),
    TWJMJZZ("台湾居民居住证", 11, 13),
    QT("其他", 12, 10),
    GATJMJZZ("港澳台居民居住证", 14, 6),
    WGHZ("外国护照", 13, 7),
    FXZ("返乡证", 15, 15),
    SBZ("士兵证", 16, 16),
    JSZZ("驾驶执照", 17, 17),
    XSZ("学生证", 18, 18),
    WJSFZM("武警身份证明", 19, 19),
    AMJMSFZ("澳门居民身份证", 20, 20),
    XGYGXJMSFZ("香港永久性居民身份证", 21, 21),
    HZ("护照", 22, 22),
    ZZJGDM("组织机构代码", 23, 23),
    SHXYDM("社会信用代码", 24, 24),
    GASFZ("港澳身份证", 25, 25),
    JGZ("军官证", 26, 26),
    YH("银行", 27, 27),
    GZZ("工作证", 28, 28),
    SBH("社保号", 29, 29),
    WZJ("无证件", 30, 30),
    LWGMSFZ("老挝国民身份证", 31, 31),
    GATXZ("港澳通行证",32,32),
    TWTXZ("台湾通行证", 33, 33),
    ;

    private String name;
    private Integer valueRbs;
    private Integer valueZF;

    ZFCertTypeEnum(String name, Integer valueRbs, Integer valueZF) {
        this.name = name;
        this.valueRbs = valueRbs;
        this.valueZF = valueZF;
    }


    public static String getNameForRbs(int index) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.valueRbs == index) {
                return c.name;
            }
        }
        return "";
    }
    public static String getNameForZF(int index) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.valueZF == index) {
                return c.name;
            }
        }
        return "";
    }

    public static Integer getValueZFForRbs(int index) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.valueRbs == index) {
                return c.valueZF;
            }
        }
        return null;
    }
    public static Integer getValueRbsForZF(int index) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.valueZF == index) {
                return c.valueRbs;
            }
        }
        return null;
    }

    public static Integer getValueZF(String name) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.name.equals(name)) {
                return c.valueZF;
            }
        }
        return null;
    }

    public static Integer getValueRbs(String name) {
        for (ZFCertTypeEnum c : ZFCertTypeEnum.values()) {
            if (c.name.equals(name)) {
                return c.valueRbs;
            }
        }
        return null;
    }
}
