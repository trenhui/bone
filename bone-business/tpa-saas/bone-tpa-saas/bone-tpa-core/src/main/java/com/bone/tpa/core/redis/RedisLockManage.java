package com.bone.tpa.core.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedisLockManage {


    @Autowired
    StringRedisTemplate stringRedisTemplate;



    /**
     * 加锁,同时设置锁超时时间
     *
     * @param key 分布式锁的key
     *
     * @return
     */
    public boolean lock(String key) {
        return  tryLock(key,1800);


    }



    public boolean tryLockAndWait(String key ,Integer expireSecond ,int retryTime){
        boolean ret = tryLock(key,expireSecond);

        if(ret){
            return ret;
        }
        if(retryTime<=0){
            retryTime = 5;
        }
        while (retryTime > 0){

            retryTime--;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            log.info("--DistributedLock tryLockAndWait--lockId:{}, retryTime:{}",key, retryTime);
            ret = tryLock(key,expireSecond);
            if(ret){
                return ret;
            }
        }
        return false;
    }

    public boolean tryLock(String key, Integer expireTime) {
        try {
            Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", expireTime, TimeUnit.SECONDS);
            return success != null && success;
        } catch (Exception e) {
            log.warn("--DistributedLock getLock--lockId:{}, millisecond:{}, exception:{}",
                    key, expireTime, e);
        }
        return Boolean.FALSE;
    }

    public void unlock(String resource) {
        try {
            stringRedisTemplate.delete(resource);
        } catch (Exception e) {
            log.error("--DistributedLock releaseLock--lockId:"+resource+", exception"
                    , e);
        }
    }
}
