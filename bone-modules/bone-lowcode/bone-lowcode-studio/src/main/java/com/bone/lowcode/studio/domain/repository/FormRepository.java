package com.bone.lowcode.studio.domain.repository;

import com.bone.core.domain.BaseRepository;
import com.bone.lowcode.studio.domain.model.Form;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 表单
 * @author 梅山源码
 */
@Repository
public interface FormRepository extends BaseRepository<Form,Long>{

}
