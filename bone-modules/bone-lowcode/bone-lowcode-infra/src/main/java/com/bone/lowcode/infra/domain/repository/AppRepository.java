package com.bone.lowcode.infra.domain.repository;

import com.bone.core.domain.BaseRepository;
import com.bone.lowcode.infra.domain.model.App;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author renhui.trh
 */
@Mapper
public interface AppRepository extends BaseRepository<App,Long>
{
    /**
     * #{#news.newsContent}#{app.code}
     * getAppByLimit
     * @param app
     * @param offset
     * @param pageSize
     * @return
     */
    @Query("select * from system_app where code=:#{#app?.code} limit :offset,:pageSize")
    List<App> customQuery(@Param("app")App app, @Param("offset") Integer offset,@Param("pageSize") Integer pageSize);
}
