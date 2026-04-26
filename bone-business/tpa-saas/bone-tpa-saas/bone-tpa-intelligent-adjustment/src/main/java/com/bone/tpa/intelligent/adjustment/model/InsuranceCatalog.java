package com.bone.tpa.intelligent.adjustment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 保险目录（值对象）
 * 该类用于表示保险目录的概念，没有标识符和生命周期
 */
@Getter
@RequiredArgsConstructor
public class InsuranceCatalog {

    /**
     * 保险目录名称
     */
    @NotBlank
    @Size(max = 50)
    private final String catalogName;

    /**
     * 目录描述
     */
    private final String description;

    @Override
    public String toString() {
        return "InsuranceCatalog{" +
                "catalogName='" + catalogName + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
