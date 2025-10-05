package com.bone.core.domain.entity;

import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 基础实体类，表示具有主键id的实体。
 *
 * @param <ID> 主键id的类型
 */
@Data
public class Entity<ID> implements Serializable {

    /**
     * 实体的主键id
     */
    @Schema(description = "主键id")
    @JsonSerialize(using = EntityIdSerializer.class)
    @GeneratedValue(strategy= GenerationStrategy.IDENTITY)
    private ID id;

    /**
     * 构造方法，接受id作为参数
     */
    public Entity(ID id) {
        this.id = id;
    }

    /**
     * 默认构造函数
     */
    public Entity() {
        // 默认构造函数，空实现
    }
}