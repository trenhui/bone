package com.bone.lowcode.infra.domain.init;

import com.bone.lowcode.infra.application.service.OptionSetApplicationService;
import com.bone.lowcode.infra.domain.constant.Constant;
import com.bone.lowcode.infra.domain.service.OptionSetVersionService;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSetVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
//@Component
public class InitHandle implements CommandLineRunner {

    @Autowired
    private OptionSetApplicationService optionSetApplicationService;
    @Autowired
    private OptionSetVersionService optionSetVersionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        //清空旧缓存
        Set<String> keySet = getKeysWithPrefix(redisTemplate, Constant.OPTION_SET_KEY);
        if (!CollectionUtils.isEmpty(keySet)) {
            redisTemplate.delete(keySet);
            log.info("清空选项集旧缓存,keys:{}", keySet);
        }

        //创建新缓存
        List<OptionSetVersion> latestOptionSetVersionList = optionSetVersionService.getLatestOptionSetVersionList();
        for (OptionSetVersion version : latestOptionSetVersionList) {
            OptionSet optionSet = version.getOptionSet();
            List<OptionSet> optionSetList = version.getOptionSetList();
            optionSetApplicationService.synchronizeOptionSetToRedis(optionSet.getCode(), optionSetList);
        }
    }

    public Set<String> getKeysWithPrefix(RedisTemplate<String, ?> redisTemplate, String prefix) {
        RedisCallback<Set<String>> callback = new RedisCallback<>() {
            @Override
            public Set<String> doInRedis(RedisConnection connection) throws DataAccessException {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(prefix + "*")
                        .count(600) //每批扫描数量
                        .build();

                Set<String> keys = new HashSet<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    cursor.forEachRemaining(keyBytes ->
                            keys.add(new String(keyBytes, StandardCharsets.UTF_8)));
                }
                return keys;
            }
        };
        return redisTemplate.execute(callback);
    }
}
