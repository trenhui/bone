package com.bone.core.domain.id;

// ID生成策略类型
public enum GenerationStrategy {
  IDENTITY, // 数据库自增
  UUID, // 程序生成UUID
  DISTRIBUTED_ID, // 分布式ID生成（如雪花算法）
  SEQUENCE, // 数据库序列
  CUSTOM // 自定义生成器
}
