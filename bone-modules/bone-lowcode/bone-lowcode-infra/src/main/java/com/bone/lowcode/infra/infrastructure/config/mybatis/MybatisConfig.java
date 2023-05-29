package com.bone.lowcode.infra.infrastructure.config.mybatis;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.id.IdGenerator;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.jdbc.core.convert.BasicJdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.mybatis.MyBatisDataAccessStrategy;
import org.springframework.data.jdbc.mybatis.NamespaceStrategy;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.repository.config.MyBatisJdbcConfiguration;
import org.springframework.data.relational.core.dialect.HsqlDbDialect;
import org.springframework.data.relational.core.dialect.MySqlDialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.event.BeforeSaveEvent;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

/**
 * @author renhui.trh
 */
@Configuration
//@EnableJdbcRepositories
//@Import(MyBatisJdbcConfiguration.class)
public class MybatisConfig {

    @Value("${mybatis.mapper-locations:mapper/**.xml}")
    private String mapperLocations;

    //定义bean，返回MapperScannerConfigurer对象
    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer msc = new MapperScannerConfigurer();
        msc.setBasePackage("com.bone.lowcode.infra.system.infrastructure.persistence.mybatis");
        return msc;
    }

    @Bean
    public SqlSessionFactoryBean sqlSessionFactoryBean(DataSource dataSource) throws IOException {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        // 时候输出mybatis相关日志
        configuration.setLogImpl(StdOutImpl.class);
        // 开启驼峰命名
        configuration.setMapUnderscoreToCamelCase(true);
        sqlSessionFactoryBean.setConfiguration(configuration);
        sqlSessionFactoryBean.setDataSource(dataSource);
        sqlSessionFactoryBean.setTypeAliasesPackage("com.bone.lowcode.infra.system.domain.model");

        // 设置pageHelper分页插件
        // PageHelper pageHelper = new PageHelper();
        //Properties properties = new Properties();
        // 指定数据库方言
        //properties.setProperty("dialect", dialect);
        //pageHelper.setProperties(properties);
        //sqlSessionFactoryBean.setPlugins(pageHelper);
        // 设置mapper.xml路径
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        if (!StringUtils.hasText(mapperLocations)) {
            mapperLocations = "mapper/*.xml";
        }
        sqlSessionFactoryBean.setMapperLocations(resolver.getResources(mapperLocations));// 设置mapper文件扫描路径
        return sqlSessionFactoryBean;
    }

    @Bean
    SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

//    @Bean
//    public ApplicationListener<BeforeSaveEvent> idSetting() {
//
//        return event -> {
//
//            if (event.getEntity() instanceof AbstractEntity) {
//
//                AbstractEntity entity = (AbstractEntity) event.getEntity();
//                if (entity.getId() == null) {
//                    entity.setId(IdGenerator.generateLongID());
//                }
//            }
//        };
//    }


//    @Bean
//    @Primary
//    MyBatisDataAccessStrategy dataAccessStrategy(SqlSession sqlSession) {
//
//        RelationalMappingContext context = new JdbcMappingContext();
//        JdbcConverter converter = new BasicJdbcConverter(context, (Identifier, path) -> null);
//
//        MyBatisDataAccessStrategy strategy = new MyBatisDataAccessStrategy(sqlSession,
//                MySqlDialect.INSTANCE.getIdentifierProcessing());
//
//        strategy.setNamespaceStrategy(new NamespaceStrategy() {
//            @Override
//            public String getNamespace(Class<?> domainType) {
//                String namespace=domainType.getPackage().getName() + ".mapper." + domainType.getSimpleName() + "Mapper";
//                System.out.println("namespace:"+namespace);
//
//                namespace ="com.bone.lowcode.infra.system.infrastructure.persistence.mybatis.AppRepositoryMybatis";
//
//                return namespace;
//            }
//        });
//
//        return strategy;
//    }
}