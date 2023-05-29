package com.bone.lowcode.infra.infrastructure.persistence;

import com.bone.lowcode.infra.domain.repository.AppRepository;
import com.bone.lowcode.infra.domain.model.App;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author renhui.trh
 */
//@Mapper
public interface AppRepositoryMybatis extends AppRepository
{
    /**
     * 测试自定义sql
     * @param offset
     * @param pageSize
     * @return
     */
    @Query("select * from system_app limit :offset,:pageSize")
    List<App> findAllPaged(@Param("offset") Integer  offset, @Param("pageSize") Integer pageSize);

    /**
     * #{#news.newsContent}#{app.code}
     * getAppByLimit
     * @param app
     * @param offset
     * @param pageSize
     * @return
     */
    @Query("select * from system_app where code=:#{#app?.code} limit :offset,:pageSize")
    List<App> getAppByLimit(@Param("app")App app, @Param("offset") Integer offset,@Param("pageSize") Integer pageSize);
}
