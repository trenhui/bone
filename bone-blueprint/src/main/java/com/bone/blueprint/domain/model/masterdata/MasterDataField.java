package com.bone.blueprint.domain.model.masterdata;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataField extends AggregateRoot<Long> {
    private Long masterDataEntityId;
    private String name;
    private String type;
    private int length;
    private boolean required;
    
    public static MasterDataField create(Long masterDataEntityId, String name, String type, int length, boolean required) {
        MasterDataField field = new MasterDataField();
        field.masterDataEntityId = masterDataEntityId;
        field.name = name;
        field.type = type;
        field.length = length;
        field.required = required;
        return field;
    }
    
    public void update(String name, String type, int length, boolean required) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.required = required;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
