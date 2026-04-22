package com.bone.masterdata.domain.model.field;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.field.vo.FieldCode;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.model.field.vo.MasterDataFieldId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataField extends AbstractEntity<MasterDataFieldId> {
    private MasterDataEntityId masterDataEntityId;
    private FieldName name;
    private FieldCode code;
    private String type;
    private Integer length;
    private Boolean required;
    private String defaultValue;
    private String description;
    private Integer sortOrder;

    public static MasterDataField create(MasterDataEntityId masterDataEntityId, FieldName name, FieldCode code, String type,
                                       Integer length, Boolean required, String defaultValue,
                                       String description, Integer sortOrder) {
        MasterDataField field = new MasterDataField();
        field.masterDataEntityId = masterDataEntityId;
        field.name = name;
        field.code = code;
        field.type = type;
        field.length = length;
        field.required = required;
        field.defaultValue = defaultValue;
        field.description = description;
        field.sortOrder = sortOrder;
        return field;
    }

    public void update(FieldName name, String type, Integer length, Boolean required, String defaultValue,
                      String description, Integer sortOrder) {
        this.name = name;
        this.type = type;
        this.length = length;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    // 仅供 SDK 回填 ID 使用
    void setId(MasterDataFieldId id) {
        super.setId(id);
    }
}
