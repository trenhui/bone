package com.bone.core.domain;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.BizException;
import com.bone.core.result.PageConverter;
import com.bone.core.result.PageResult;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

/**
 * @author renhui.trh
 */
public class BaseServiceImpl<TEntity extends AbstractEntity, ID> implements BaseService<TEntity, ID> {

    private final BaseRepository<TEntity, ID> baseRepository;

    public BaseServiceImpl(BaseRepository<TEntity, ID> baseRepository) {
        this.baseRepository = baseRepository;
    }

    private void validateExists(ID id) {
        if (!baseRepository.existsById(id)) {
            throw new BizException("can't find entity by id  " + id);
        }
    }

    @Override
    public <S extends TEntity> S create(S entity) {
        return baseRepository.save(entity);
    }

    @Override
    public <S extends TEntity> void update(S entity) {
        validateExists((ID) entity.getId());
        baseRepository.save(entity);
    }

    @Override
    public void delete(ID id) {
        validateExists(id);
        baseRepository.deleteById(id);
    }

    @Override
    public void batchDelete(Collection<ID> ids) {
        baseRepository.deleteAllById(ids);
    }

    @Override
    public TEntity get(ID id) {
        return baseRepository.findById(id).orElse(null);
    }

    @Override
    public List<TEntity> list(Collection<ID> ids) {
        return (List<TEntity>) baseRepository.findAllById(ids);
    }

    @Override
    public List<TEntity> list(TEntity entity) {
        Example<TEntity> example = Example.of(entity);
        return baseRepository.findAll(example);
    }

    @Override
    public PageResult<TEntity> page(TEntity entity, Pageable pageable) {
        Example<TEntity> example = Example.of(entity);
        return PageConverter.toPageResult(baseRepository.findAll(example, pageable));
    }
}
