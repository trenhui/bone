package com.bone.tpa.api.enums;

import lombok.Getter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//普康影像件类别code、中文名枚举
@Getter
public enum PKImageMapEnum {
    INVOICE("1", "发票", new HashSet<>(Arrays.asList("发票"))),
    MEDICAL_RECORD("2", "病例", new HashSet<>(Arrays.asList("病历"))),
    APPLICATION("3", "申请书", new HashSet<>(Arrays.asList("申请书"))),
    ID_CARD("4", "身份证资料", new HashSet<>(Arrays.asList("身份证"))),
    OTHER("5", "其他", new HashSet<>(Arrays.asList("其它", "银行卡"))),
    UN_CLASSIFY("6", "未分类", new HashSet<>());

    private final String code;//普康影像件类别code
    private final String chineseName;//普康影像件类别中文名
    private final Set<String> classifyNameSet;//普康影像件分类接口的分类结果

    PKImageMapEnum(String code, String desc, Set<String> classifyNameSet) {
        this.code = code;
        this.chineseName = desc;
        this.classifyNameSet = classifyNameSet;
    }

    public static String getCodeByClassifyName(String classifyName) {
        if (!StringUtils.hasText(classifyName)) {
            return null;
        }
        for (PKImageMapEnum item : PKImageMapEnum.values()) {
            if (item.getClassifyNameSet().contains(classifyName)) {
                return item.getCode();
            }
        }
        return null;
    }

    public static String getCodeByCn(String cn) {
        for (PKImageMapEnum item : PKImageMapEnum.values()) {
            if (item.getChineseName().contains(cn)) {
                return item.getCode();
            }
        }
        return null;
    }

    public static String getCnByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        for (PKImageMapEnum item : PKImageMapEnum.values()) {
            if (code.equals(item.getCode())) {
                return item.getChineseName();
            }
        }
        return null;
    }

    public static List<String> getAllPkCode() {
        return Arrays.stream(PKImageMapEnum.values()).map(PKImageMapEnum::getCode).collect(Collectors.toList());
    }
}
