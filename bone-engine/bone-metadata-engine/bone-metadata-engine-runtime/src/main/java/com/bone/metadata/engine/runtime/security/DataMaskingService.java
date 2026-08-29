package com.bone.metadata.engine.runtime.security;

/** 数据脱敏服务接口 提供不同类型数据的脱敏功能 */
public interface DataMaskingService {

  /**
   * 应用数据脱敏
   *
   * @param value 原始值
   * @param rule 脱敏规则
   * @return 脱敏后的值
   */
  Object applyMasking(Object value, DataMaskingRule rule);

  /**
   * 手机号脱敏
   *
   * @param phoneNumber 手机号
   * @param showFirst 显示前几位
   * @param showLast 显示后几位
   * @return 脱敏后的手机号
   */
  String maskPhoneNumber(String phoneNumber, int showFirst, int showLast);

  /**
   * 身份证号脱敏
   *
   * @param idCardNumber 身份证号
   * @param showFirst 显示前几位
   * @param showLast 显示后几位
   * @return 脱敏后的身份证号
   */
  String maskIdCardNumber(String idCardNumber, int showFirst, int showLast);

  /**
   * 银行卡号脱敏
   *
   * @param bankCardNumber 银行卡号
   * @param showFirst 显示前几位
   * @param showLast 显示后几位
   * @return 脱敏后的银行卡号
   */
  String maskBankCardNumber(String bankCardNumber, int showFirst, int showLast);

  /**
   * 姓名脱敏
   *
   * @param name 姓名
   * @return 脱敏后的姓名
   */
  String maskName(String name);

  /**
   * 邮箱脱敏
   *
   * @param email 邮箱
   * @return 脱敏后的邮箱
   */
  String maskEmail(String email);

  /**
   * 地址脱敏
   *
   * @param address 地址
   * @param showFirst 显示前几位
   * @return 脱敏后的地址
   */
  String maskAddress(String address, int showFirst);

  /**
   * 自定义正则表达式脱敏
   *
   * @param value 原始值
   * @param pattern 正则表达式
   * @param replacement 替换内容
   * @return 脱敏后的值
   */
  String maskWithRegex(String value, String pattern, String replacement);

  /**
   * 获取脱敏规则类型
   *
   * @return 支持的脱敏规则类型列表
   */
  String[] getSupportedMaskingTypes();
}
