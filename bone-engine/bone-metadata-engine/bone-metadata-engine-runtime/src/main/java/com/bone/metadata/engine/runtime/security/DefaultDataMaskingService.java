package com.bone.metadata.engine.runtime.security;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 默认数据脱敏服务实现 提供各种数据类型的脱敏功能 */
public class DefaultDataMaskingService implements DataMaskingService {

  // 支持的脱敏类型
  private static final String[] SUPPORTED_TYPES = {
    "PHONE", "ID_CARD", "BANK_CARD", "NAME", "EMAIL", "ADDRESS", "GENERAL", "REGEX"
  };

  @Override
  public Object applyMasking(Object value, DataMaskingRule rule) {
    if (value == null || rule == null) {
      return value;
    }

    String valueStr = value.toString();
    String type = rule.getType(); // 使用getType()方法

    if (type == null) {
      return value;
    }

    switch (type.toUpperCase()) {
      case DataMaskingRule.TYPE_PHONE:
        return maskPhoneNumber(valueStr, 3, 4); // 默认值
      case DataMaskingRule.TYPE_ID_CARD:
        return maskIdCardNumber(valueStr, 6, 4); // 默认值
      case DataMaskingRule.TYPE_BANK_CARD:
        return maskBankCardNumber(valueStr, 4, 4); // 默认值
      case DataMaskingRule.TYPE_NAME:
        return maskName(valueStr);
      case DataMaskingRule.TYPE_EMAIL:
        return maskEmail(valueStr);
      case DataMaskingRule.TYPE_ADDRESS:
        return maskAddress(valueStr, 4); // 默认值
      case "FULL": // 使用字符串字面量
        return maskFull(valueStr);
      case DataMaskingRule.TYPE_GENERAL:
        return maskGeneral(valueStr, 2, 2); // 默认值
      default:
        // 如果有pattern和replacement，使用正则脱敏
        if (rule.getPattern() != null && rule.getReplacement() != null) {
          return maskWithRegex(valueStr, rule.getPattern(), rule.getReplacement());
        }
        return maskGeneral(valueStr, 2, 2); // 默认值
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
    if (name == null || name.isEmpty()) {
      return name;
    }

    // 中文姓名脱敏
    if (name.length() == 1) {
      return name;
    } else if (name.length() == 2) {
      return name.charAt(0) + "*";
    } else if (name.length() == 3) {
      // 3个字的姓名，如：张*三
      return name.charAt(0) + "*" + name.charAt(2);
    } else {
      // 4个字及以上的姓名，保留姓和最后一个字
      StringBuilder masked = new StringBuilder();
      masked.append(name.charAt(0));
      for (int i = 1; i < name.length() - 1; i++) {
        masked.append('*');
      }
      masked.append(name.charAt(name.length() - 1));
      return masked.toString();
    }
  }

  @Override
  public String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return email;
    }

    // 邮箱脱敏：保留用户名的前两位和域名
    int atIndex = email.indexOf('@');
    String username = email.substring(0, atIndex);
    String domain = email.substring(atIndex);

    if (username.length() <= 2) {
      return username.charAt(0) + "****" + domain;
    } else {
      return username.substring(0, 2) + "****" + domain;
    }
  }

  @Override
  public String maskAddress(String address, int showFirst) {
    if (address == null || address.length() <= showFirst) {
      return address;
    }

    // 地址脱敏：保留前几位，后面用*代替
    StringBuilder masked = new StringBuilder(address.substring(0, showFirst));
    int maskLength = address.length() - showFirst;
    for (int i = 0; i < maskLength; i++) {
      masked.append('*');
    }
    return masked.toString();
  }

  @Override
  public String maskWithRegex(String value, String pattern, String replacement) {
    if (value == null || pattern == null) {
      return value;
    }

    // 使用正则表达式进行脱敏
    Pattern p = Pattern.compile(pattern);
    Matcher m = p.matcher(value);
    return m.replaceAll(replacement);
  }

  @Override
  public String[] getSupportedMaskingTypes() {
    // 返回支持的脱敏类型
    return Arrays.copyOf(SUPPORTED_TYPES, SUPPORTED_TYPES.length);
  }

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

  private String maskFull(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    // 完全脱敏，用单个星号代替
    return "*";
  }
}
