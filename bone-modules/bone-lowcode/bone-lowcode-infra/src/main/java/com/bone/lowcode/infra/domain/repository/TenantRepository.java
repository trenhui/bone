package com.bone.lowcode.infra.domain.repository;

import com.bone.core.domain.BaseRepository;
import com.bone.lowcode.infra.domain.model.Tenant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 租户
 * @author 梅山源码
 */
@Repository
public interface TenantRepository extends BaseRepository<Tenant,Long>{

}
