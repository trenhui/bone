package com.bone.core.domain;

import com.bone.core.domain.entity.AbstractEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.ListQueryByExampleExecutor;

/**
 * @author renhui.trh
 */
public interface BaseRepository<TEntity extends AbstractEntity, ID> extends CrudRepository<TEntity, ID>, ListQueryByExampleExecutor<TEntity> {
}