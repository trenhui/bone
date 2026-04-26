package com.pkh.cloud.auth.infrastructure.config;

import com.bone.core.exception.BizException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Component
public class NonceProvider implements InitializingBean {
    Set<String> nonceSet = new ConcurrentSkipListSet<>();
    volatile long lastUpdateTime;

    @Override
    public void afterPropertiesSet() throws Exception {
        nonceSet = new ConcurrentSkipListSet<>();
        lastUpdateTime = System.currentTimeMillis();
    }

    public boolean addNonce(String nonce) {
        String[] split = nonce.split(":");
//        if (System.currentTimeMillis() - lastUpdateTime > 60 * 1000){
//            nonceSet.removeIf(item -> System.currentTimeMillis() - Long.parseLong(item.split(":")[1]) > 60 * 1000);
//        }
//        if (System.currentTimeMillis() - Long.parseLong(split[1]) > 60 * 1000){
//            throw new BizException("nonce 过期");
//        }
//        if (nonceSet.contains(nonce)) {
//            throw new BizException("nonce 重复");
//        }
        nonceSet.add(nonce);
        return true;
    }


}
