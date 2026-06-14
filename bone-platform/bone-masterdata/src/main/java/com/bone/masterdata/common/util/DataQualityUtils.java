package com.bone.masterdata.common.util;

public class DataQualityUtils {
  /** 验证字符串是否为空 */
  public static boolean isEmpty(String value) {
    return value == null || value.trim().isEmpty();
  }

  /** 验证字符串长度是否在指定范围内 */
  public static boolean isLengthValid(String value, int min, int max) {
    if (value == null) return false;
    int length = value.length();
    return length >= min && length <= max;
  }

  /** 验证数字是否在指定范围内 */
  public static boolean isNumberInRange(double value, double min, double max) {
    return value >= min && value <= max;
  }

  /** 验证邮箱格式 */
  public static boolean isEmailValid(String email) {
    if (email == null) return false;
    return email.matches(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
  }

  /** 验证手机号格式 */
  public static boolean isPhoneValid(String phone) {
    if (phone == null) return false;
    return phone.matches("^1[3-9]\\d{9}$");
  }
}
