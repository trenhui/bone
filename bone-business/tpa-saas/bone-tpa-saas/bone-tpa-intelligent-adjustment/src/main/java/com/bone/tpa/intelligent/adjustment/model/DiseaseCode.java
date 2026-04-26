package com.bone.tpa.intelligent.adjustment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 疾病类型（值对象）
 * 该类用于表示疾病代码及其名称的概念，没有标识符和生命周期
 */
@Getter
@RequiredArgsConstructor
public class DiseaseCode {

    /**
     * 疾病代码
     */
    @NotBlank
    @Size(max = 20)
    private final String code;

    /**
     * 疾病名称
     */
    @NotBlank
    @Size(max = 100)
    private final String name;

    @Override
    public String toString() {
        return "DiseaseCode{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
