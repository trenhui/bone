package com.bone.core.domain.extension;

import com.bone.core.annotation.Transient;
import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支持动态扩展的业务实体基类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "支持动态扩展的业务实体基类")
public class ExtensibleObject<ID> extends TenantAbstractEntity<ID> implements Extensible {

    @Schema(description = "业务身份code")
    private String bizIdentityCode;

    @Transient
    private final Map<String, Object> extraProperties = new ConcurrentHashMap<>(64);
}