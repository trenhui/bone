package com.netease.mis.bsm.budget.domain.repository;

import com.bone.core.domain.BaseRepository;
import com.netease.mis.bsm.budget.domain.model.ProjectEmployee;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目成员
 * @author 梅山源码
 */
@Repository
public interface ProjectEmployeeRepository extends BaseRepository<ProjectEmployee,Long>{

}
