package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Id;;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.extension.Extensible;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.model.NullableConcurrentMap;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("sys_permission")
@NoArgsConstructor
@AllArgsConstructor
public class Permission extends Entity<Long>  implements Extensible {
    @Transient
    private final Map<String, Object> extraProperties = new NullableConcurrentMap<>(64);

    @Schema(description = "业务身份code")
    private String bizIdentityCode;

    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    private String permName;

    private String permCode;

    private Integer permType;

    private Long parentId;

    private String path;

    private String component;

    private String icon;

    private Integer sortOrder;

    // 创建时间：使用 LocalDateTime
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // 修改时间：使用 LocalDateTime
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改时间")
    private LocalDateTime updateTime;

    @Transient
    private Set<Role> roles = new HashSet<>();
}