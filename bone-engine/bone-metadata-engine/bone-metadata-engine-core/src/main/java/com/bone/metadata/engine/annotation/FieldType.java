package com.bone.metadata.engine.annotation;

/** 字段类型枚举 */
public enum FieldType {
  TEXT, // 文本
  TEXT_AREA, // 文本区域
  NUMBER, // 数字
  CURRENCY, // 货币
  PERCENT, // 百分比
  DATE, // 日期
  DATE_TIME, // 日期时间
  TIME, // 时间
  BOOLEAN, // 布尔值
  PICKLIST, // 选择列表
  MULTI_SELECT_PICKLIST, // 多选择列表
  LOOKUP, // 查找关系
  MASTER_DETAIL, // 主从关系
  AUTO_NUMBER, // 自动编号
  EMAIL, // 电子邮件
  PHONE, // 电话号码
  URL, // 网址
  ENCRYPTED_TEXT, // 加密文本
  FORMULA // 公式字段
}
