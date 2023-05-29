package com.bone.core.domain;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

/**
 * @author renhui.trh
 */
public interface BaseService <TEntity extends AbstractEntity,ID> {
    /**
     * 创建实体
     *
     * @param entity 实体信息
     * @return 编号
     */
    <S extends TEntity> S create(@Valid S entity);
    /**
     * 更新实体
     *
     * @param entity 实体信息
     */
    <S extends TEntity> void update(@Valid S entity);

    /**
     * 删除实体
     *
     * @param id 主键
     */
    void delete(ID id);

    /**
     * 批量删除实体
     *
     * @param ids 主键集合
     */
    void batchDelete(Collection<ID> ids);

    /**
     * 根据ID获取实体
     *
     * @param id 主键
     * @return 实体信息
     */
    TEntity get(ID id);

    /**
     * 根据ID集合查询实体列表
     *
     * @param ids 编号
     * @return 应用列表
     */
    List<TEntity> list(Collection<ID> ids);


    /***
     * 查询实体列表
     * @param entity 查询信息
     * @return
     */
    public List<TEntity> list(TEntity entity);

    /**
     * 分页查询
     *
     * @param entity 查询信息
     * @param pageable 分页查询参数
     *
     * @return 应用查询分页结果
     */
    PageResult<TEntity> page(TEntity entity, Pageable pageable);
}