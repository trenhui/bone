package com.bone.tpa.intelligent.adjustment.model.enums;


import lombok.Getter;

/**
 * 证件类型枚举 推送直付身份证类型(直付通用版本)
 */
@Getter
public enum CertificateTypeEnum {

    TYPE_SFZ("0", "a", "身份证"),
    TYPE_JGZ("1", "d", "军官证"),
    TYPE_ZGHZ("2", "ac", "中国护照"),
    TYPE_CSZ("3", "m", "出生证"),
    TYPE_YCSFZ("4", "n", "异常身份证"),
    TYPE_GAJMLWNDTXZ("5", "j", "港澳居民来往内地通行证"),
    TYPE_GATJMJZZ("6", "h", "港澳台居民居住证"),
    TYPE_WGHZ("7", "k", "外国护照"),
    TYPE_WGRYJJLSFZ("8", "u", "外国人永久居留身份证"),
    TYPE_TWJMWLDLTXZ("9", "l", "台湾居民往来大陆通行证"),
    TYPE_QT("10", "i", "其他"),
    TYPE_HKB("11", "r", "户口本"),
    TYPE_GAJMJZZ("12", "q", "港澳居民居住证"),
    TYPE_TWJMJZZ("13", "v", "台湾居民居住证"),
    TYPE_JCZ("14", "s", "警察证"),
    TYPE_FXZ("15", "b", "返乡证"),
    TYPE_SBZ("16", "p", "士兵证"),
    TYPE_JSZZ("17", "f", "驾驶执照"),
    TYPE_XSZ("18", "y", "学生证"),
    TYPE_WJSFZM("19", "z", "武警身份证明"),
    TYPE_AMJMSFZ("20", "aa", "澳门居民身份证"),
    TYPE_XGYJJMSFZ("21", "ab", "香港(永久性)居民身份证"),
    TYPE_HZ("22", "c", "护照"),
    TYPE_ZZJGDM("23", "ad", "组织机构代码"),
    TYPE_SHXYDM("24", "ae", "社会信用代码"),
    TYPE_GASFZ("25", "e", "港澳身份证"),
    TYPE_JRZ("26", "w", "军官证"),
    TYPE_YH("27", "x", "银行"),
    TYPE_GZZ("28", "af", "工作证"),
    TYPE_SBH("29", "ag", "社保号"),
    TYPE_WZJ("30", "ah", "无证件"),
    LWGMSFZ("31", "ai", "老挝国民身份证"),
    TYPE_GATXZ("32", "aj", "港澳通行证"),
    TYPE_TWTXZ("33", "ak", "台湾通行证"),
    ;

    private final String pkCode; //普康tpa code

    private final String zfCode; //普康直付 code
    private final String desc; //描述

    CertificateTypeEnum(String pkCode, String zfCode, String desc) {
        this.pkCode = pkCode;
        this.zfCode = zfCode;
        this.desc = desc;
    }
}
