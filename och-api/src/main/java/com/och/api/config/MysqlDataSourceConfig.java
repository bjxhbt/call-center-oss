package com.och.api.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bind Druid-specific settings explicitly for the opt-in MySQL deployment. */
@Configuration
@ConditionalOnProperty(prefix = "och.database", name = "type", havingValue = "mysql")
public class MysqlDataSourceConfig {
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public DruidDataSource mysqlDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(DruidDataSource.class).build();
    }
}
