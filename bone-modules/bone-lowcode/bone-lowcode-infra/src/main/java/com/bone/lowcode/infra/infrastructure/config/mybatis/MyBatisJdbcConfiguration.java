/*
package com.bone.lowcode.infra.system.config;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.*;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.mybatis.MyBatisDataAccessStrategy;
import org.springframework.data.jdbc.mybatis.NamespaceStrategy;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

*/
/**
 * @author renhui.trh
 *//*

@Configuration(proxyBeanMethods = false)
public class MyBatisJdbcConfiguration extends AbstractJdbcConfiguration {

    private @Autowired SqlSession session;

    @Bean
    @Override
    public DataAccessStrategy dataAccessStrategyBean(NamedParameterJdbcOperations operations, JdbcConverter jdbcConverter, JdbcMappingContext context, Dialect dialect) {

        return MyBatisDataAccessStrategy.createCombinedAccessStrategy(context, jdbcConverter, operations, session, new MyBatisNamespaceStraegy(),dialect);
       // return new DefaultDataAccessStrategy(new SqlGeneratorSource(context, jdbcConverter, dialect), context, jdbcConverter, operations, new SqlParametersFactory(context, jdbcConverter, dialect), new InsertStrategyFactory(operations, new BatchJdbcOperations(operations.getJdbcOperations()), dialect));
    }
}
*/
