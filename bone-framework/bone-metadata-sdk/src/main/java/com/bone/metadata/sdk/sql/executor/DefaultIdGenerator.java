package com.bone.metadata.sdk.sql.executor;

import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.IdGenerator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.Map;
import java.util.UUID;

public class DefaultIdGenerator implements IdGenerator {
    private final NamedParameterJdbcOperations jdbc;

    public DefaultIdGenerator(NamedParameterJdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Object generateId(GenerationStrategy strategy, Object entity) {
        return switch (strategy) {
            case IDENTITY -> null; // 由数据库自增生成
            case UUID -> UUID.randomUUID().toString();
            case SEQUENCE -> {
                String sequenceName = "my_sequence";
                yield jdbc.queryForObject("SELECT nextval(:seq)", Map.of("seq", sequenceName), Long.class);
            }
            case CUSTOM -> com.bone.core.id.IdGenerator.generateLongID();
            case DISTRIBUTED_ID -> com.bone.core.id.IdGenerator.generateLongID();
            default -> throw new UnsupportedOperationException("Unsupported parser: " + strategy);
        };
    }
}