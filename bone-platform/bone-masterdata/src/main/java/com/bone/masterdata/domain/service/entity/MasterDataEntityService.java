package com.bone.masterdata.domain.service.entity;

import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.MasterDataField;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;

/**
 * 主数据实体领域服务
 * 处理主数据实体相关的业务规则，涉及多个聚合根的操作
 */
@RequiredArgsConstructor
public class MasterDataEntityService {
    private final MasterDataEntityRepository entityRepository;
    private final MasterDataFieldRepository fieldRepository;

    /**
     * 创建主数据实体
     * @param name 实体名称
     * @param description 描述
     * @param category 分类
     * @return 创建后的实体
     */
    public MasterDataEntity createEntity(MasterDataEntityName name, String description, String category) {
        boolean exists = QueryBuilder.from(MasterDataEntity.class)
                .where(MasterDataEntity::getName).eq(name.value())
                .exists();
        if (exists) {
            throw new DomainException("主数据实体名称已存在");
        }
        return MasterDataEntity.create(name, description, category);
    }

    /**
     * 发布主数据实体
     * @param entityId 实体ID
     */
    public void publishEntity(MasterDataEntityId entityId) {
        MasterDataEntity entity = entityRepository.findById(entityId);
        if (entity == null) {
            throw new DomainException("主数据实体不存在");
        }
        entity.publish();
        entityRepository.save(entity);
    }

    /**
     * 获取实体的字段数量
     * @param entityId 实体ID
     * @return 字段数量
     */
    public int getFieldCount(MasterDataEntityId entityId) {
        long count = QueryBuilder.from(MasterDataField.class)
                .where(MasterDataField::getMasterDataEntityId).eq(entityId.getValue())
                .count();
        return (int) count;
    }
}