package com.bone.metadata.engine.runtime;

/** 建模字段与物理列映射（默认列名 = field.code）。 */
public record RuntimeFieldColumn(
    /** 字段编码（= 物理列名） */
    String code,
    /** 声明类型（STRING/INTEGER/LONG/DECIMAL/BOOLEAN/DATETIME/...），用于运行期写入校验 */
    String type,
    /** 是否必填（写前校验） */
    boolean required,
    /** 是否唯一（写前查重） */
    boolean unique,
    /** 是否主键 */
    boolean primaryKey) {}
