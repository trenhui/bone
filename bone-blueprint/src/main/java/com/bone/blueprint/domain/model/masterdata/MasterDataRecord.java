package com.bone.blueprint.domain.model.masterdata;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Date;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MasterDataRecord extends AggregateRoot<Long> {
    private Long masterDataEntityId;
    private String data;
    private String status;
    private Date publishTime;
    
    public static MasterDataRecord create(Long masterDataEntityId, String data) {
        MasterDataRecord record = new MasterDataRecord();
        record.masterDataEntityId = masterDataEntityId;
        record.data = data;
        record.status = "DRAFT";
        return record;
    }
    
    public void update(String data) {
        this.data = data;
    }
    
    public void publish() {
        this.status = "PUBLISHED";
        this.publishTime = new Date();
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
