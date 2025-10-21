package com.bone.smartmeta.engine.security;

import com.bone.smartmeta.engine.security.FieldLevelSecurityManager.DataMaskingRule;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认数据脱敏服务实现
 * 提供各种数据类型的脱敏功能
 */
@Service
public class DefaultDataMaskingService implements DataMaskingService {

    // 支持的脱敏类型
    private static final String[] SUPPORTED_TYPES = {
            "PHONE", "ID_CARD", "BANK_CARD", "NAME", "EMAIL", "ADDRESS", "GENERAL", "REGEX"
    };

    @Override
    public Object applyMasking(Object value, DataMaskingRule rule) {
        if (value == null) {
            return null;
        }

        String valueStr = value.toString();
        String type = rule.getType();

        if (type == null) {
            return value;
        }

        switch (type.toUpperCase()) {
            case "PHONE":
                return maskPhoneNumber(valueStr, 3, 4);
            case "ID_CARD":
                return maskIdCardNumber(valueStr, 6, 4);
            case "BANK_CARD":
                return maskBankCardNumber(valueStr, 4, 4);
            case "NAME":
                return maskName(valueStr);
            case "EMAIL":
                return maskEmail(valueStr);
            case "ADDRESS":
                return maskAddress(valueStr, 6);
            case "REGEX":
                if (rule.getPattern() != null && rule.getReplacement() != null) {
                    return maskWithRegex(valueStr, rule.getPattern(), rule.getReplacement());
                }
                // 如果没有提供正则表达式，则使用通用脱敏
                return maskGeneral(valueStr, 2, 2);
            case "GENERAL":
            default:
                return maskGeneral(valueStr, 2, 2);
        }
    }

    @Override
    public String maskPhoneNumber(String phoneNumber, int showFirst, int showLast) {
        if (phoneNumber == null || phoneNumber.length() <= showFirst + showLast) {
            return phoneNumber;
        }

        // 简单的手机号脱敏：保留前3位和后4位
        StringBuilder masked = new StringBuilder(phoneNumber.substring(0, showFirst));
        int maskLength = phoneNumber.length() - showFirst - showLast;
        for (int i = 0; i < maskLength; i++) {
            masked.append('*');
        }
        masked.append(phoneNumber.substring(phoneNumber.length() - showLast));
        return masked.toString();
    }

    @Override
    public String maskIdCardNumber(String idCardNumber, int showFirst, int showLast) {
        if (idCardNumber == null || idCardNumber.length() <= showFirst + showLast) {
            return idCardNumber;
        }

        // 身份证号脱敏：保留前6位和后4位
        StringBuilder masked = new StringBuilder(idCardNumber.substring(0, showFirst));
        int maskLength = idCardNumber.length() - showFirst - showLast;
        for (int i = 0; i < maskLength; i++) {
            masked.append('*');
        }
        masked.append(idCardNumber.substring(idCardNumber.length() - showLast));
        return masked.toString();
    }

    @Override
    public String maskBankCardNumber(String bankCardNumber, int showFirst, int showLast) {
        if (bankCardNumber == null || bankCardNumber.length() <= showFirst + showLast) {
            return bankCardNumber;
        }

        // 银行卡号脱敏：保留前4位和后4位
        StringBuilder masked = new StringBuilder(bankCardNumber.substring(0, showFirst));
        int maskLength = bankCardNumber.length() - showFirst - showLast;
        for (int i = 0; i < maskLength; i++) {
            masked.append('*');
        }
        masked.append(bankCardNumber.substring(bankCardNumber.length() - showLast));
        return masked.toString();
    }

    @Override
    public String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }

        // 姓名脱敏：
        // 1. 单姓单名：姓全显示，名用*代替
        // 2. 单姓复名：姓全显示，除最后一个名外其余用*代替
        // 3. 复姓单名：除最后一个字外其余用*代替
        // 4. 复姓复名：除最后一个字外其余用*代替
        StringBuilder masked = new StringBuilder();
        if (name.length() == 2) {
            // 单姓单名
            masked.append(name.charAt(0)).append('*');
        } else if (name.length() == 3) {
            // 可能是单姓复名或复姓单名
            // 简化处理：保留姓和最后一个名
            masked.append(name.charAt(0)).append('*').append(name.charAt(2));
        } else {
            // 姓名较长
            masked.append(name.charAt(0));
            for (int i = 1; i < name.length() - 1; i++) {
                masked.append('*');
            }
            masked.append(name.charAt(name.length() - 1));
        }
        return masked.toString();
    }

    @Override
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        // 邮箱脱敏：用户名部分保留前两位和后两位
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return email;
        }

        String username = parts[0];
        String domain = parts[1];

        if (username.length() <= 4) {
            // 如果用户名较短，只保留第一个字符
            return username.charAt(0) + "***@" + domain;
        }

        String maskedUsername = username.substring(0, 2) + "***" + 
                               username.substring(username.length() - 2);
        return maskedUsername + "@" + domain;
    }

    @Override
    public String maskAddress(String address, int showFirst) {
        if (address == null || address.length() <= showFirst) {
            return address;
        }

        // 地址脱敏：保留前几位
        StringBuilder masked = new StringBuilder(address.substring(0, showFirst));
        int maskLength = Math.min(address.length() - showFirst, 8);
        for (int i = 0; i < maskLength; i++) {
            masked.append('*');
        }
        return masked.toString();
    }

    @Override
    public String maskWithRegex(String value, String pattern, String replacement) {
        if (value == null || pattern == null || replacement == null) {
            return value;
        }

        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(value);
            return m.replaceAll(replacement);
        } catch (Exception e) {
            // 如果正则表达式有误，使用通用脱敏
            return maskGeneral(value, 2, 2);
        }
    }

    @Override
    public String[] getSupportedMaskingTypes() {
        return Arrays.copyOf(SUPPORTED_TYPES, SUPPORTED_TYPES.length);
    }

    /**
     * 通用脱敏方法
     * @param value 原始值
     * @param showFirst 显示前几位
     * @param showLast 显示后几位
     * @return 脱敏后的值
     */
    private String maskGeneral(String value, int showFirst, int showLast) {
        if (value == null || value.length() <= showFirst + showLast) {
            return value;
        }

        StringBuilder masked = new StringBuilder(value.substring(0, showFirst));
        int maskLength = value.length() - showFirst - showLast;
        for (int i = 0; i < maskLength; i++) {
            masked.append('*');
        }
        masked.append(value.substring(value.length() - showLast));
        return masked.toString();
    }
}