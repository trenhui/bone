package com.bone.masterdata.domain.model.entity;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.model.entity.event.MasterDataFieldAddedEvent;
import com.bone.masterdata.domain.model.entity.vo.MasterDataFieldName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataField extends AggregateRoot<Long> {
    private Long masterDataEntityId;
    private MasterDataFieldName name;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MasterDataField create(
            Long masterDataEntityId,
            MasterDataFieldName name,
            String type,
            Integer length,
            Boolean required,
            String defaultValue,
            String description
    ) {
        MasterDataField field = new MasterDataField();
        field.masterDataEntityId = masterDataEntityId;
        field.name = name;
        field.type = type;
        field.length = length;
        field.required = required;
        field.defaultValue = defaultValue;
        field.description = description;
        field.createdAt = LocalDateTime.now();
        field.updatedAt = LocalDateTime.now();
        field.addDomainEvent(new MasterDataFieldAddedEvent(field));
        return field;
    }

    public void update(
            MasterDataFieldName name,
            String type,
            Integer length,
            Boolean required,
            String defaultValue,
            String description
    ) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }
}