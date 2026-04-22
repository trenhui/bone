package com.bone.blueprint.domain.model.masterdata;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataEntity extends AggregateRoot<Long> {
    private String name;
    private String description;
    private String category;
    private String status;
    
    public static MasterDataEntity create(String name, String description, String category) {
        MasterDataEntity entity = new MasterDataEntity();
        entity.name = name;
        entity.description = description;
        entity.category = category;
        entity.status = "DRAFT";
        return entity;
    }
    
    public void update(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }
    
    public void publish() {
        this.status = "PUBLISHED";
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
