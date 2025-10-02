package com.bone.core.domain.extension;

import com.bone.core.tenant.TenantAbstractDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class ExtensibleDTO<ID> extends TenantAbstractDTO<ID> {

    private Map<String, Object> extraProperties = new ConcurrentHashMap<>(64);

}