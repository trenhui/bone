package com.bone.metadata.engine.runtime;

/** 建模字段与物理列映射（默认列名 = field.code） */
public record RuntimeFieldColumn(String code, boolean required, boolean primaryKey) {}
