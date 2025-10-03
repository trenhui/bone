package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.GeneratedValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table("users")
public class User extends AbstractEntity<Long> {
    private String name;
    private Long roleId;

    /**
     * 实体的主键id，使用自定义生成策略
     */
    @Schema(description = "主键id")
    @JsonSerialize(using = ToStringSerializer.class)
    @Id
    @GeneratedValue(strategy = GenerationStrategy.CUSTOM)
    private Long id;

    // 手动编写包含父类字段的构造函数
    public User(Long id, String name, Long roleId,
                Date createTime, Long createBy,
                Date updateTime, Long updateBy, Boolean deleted) {
        super(id, createTime, createBy, updateTime, updateBy, deleted);
        this.id = id;
        this.name = name;
        this.roleId = roleId;
    }
}