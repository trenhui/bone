package com.bone.core.event;

import com.bone.core.id.IdGenerator;
import lombok.Data;

import java.util.Date;

/**
 * @author renhui.trh
 */
@Data
public class DomainEvent<TEntity> {
    String id= IdGenerator.generateSnowFlakeID();
    private TEntity sourceEntity;
    private Object eventData;
    Date timestamp;
}