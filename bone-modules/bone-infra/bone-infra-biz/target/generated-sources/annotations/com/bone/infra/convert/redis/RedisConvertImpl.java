package com.bone.infra.convert.redis;

import com.bone.base.redis.core.RedisKeyDefine;
import com.bone.infra.controller.admin.redis.vo.RedisKeyDefineRespVO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class RedisConvertImpl implements RedisConvert {

    @Override
    public List<RedisKeyDefineRespVO> convertList(List<RedisKeyDefine> list) {
        if ( list == null ) {
            return null;
        }

        List<RedisKeyDefineRespVO> list1 = new ArrayList<RedisKeyDefineRespVO>( list.size() );
        for ( RedisKeyDefine redisKeyDefine : list ) {
            list1.add( redisKeyDefineToRedisKeyDefineRespVO( redisKeyDefine ) );
        }

        return list1;
    }

    protected RedisKeyDefineRespVO redisKeyDefineToRedisKeyDefineRespVO(RedisKeyDefine redisKeyDefine) {
        if ( redisKeyDefine == null ) {
            return null;
        }

        RedisKeyDefineRespVO.RedisKeyDefineRespVOBuilder redisKeyDefineRespVO = RedisKeyDefineRespVO.builder();

        redisKeyDefineRespVO.keyTemplate( redisKeyDefine.getKeyTemplate() );
        redisKeyDefineRespVO.keyType( redisKeyDefine.getKeyType() );
        redisKeyDefineRespVO.valueType( redisKeyDefine.getValueType() );
        redisKeyDefineRespVO.timeoutType( redisKeyDefine.getTimeoutType() );
        redisKeyDefineRespVO.timeout( redisKeyDefine.getTimeout() );
        redisKeyDefineRespVO.memo( redisKeyDefine.getMemo() );

        return redisKeyDefineRespVO.build();
    }
}
