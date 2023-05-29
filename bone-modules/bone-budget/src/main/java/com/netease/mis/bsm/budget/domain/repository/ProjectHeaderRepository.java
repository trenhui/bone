package com.netease.mis.bsm.budget.domain.repository;

import com.bone.core.domain.BaseRepository;
import com.netease.mis.bsm.budget.domain.model.ProjectHeader;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目单头
 * @author 梅山源码
 */
@Repository
public interface ProjectHeaderRepository extends BaseRepository<ProjectHeader,Long>{

}
