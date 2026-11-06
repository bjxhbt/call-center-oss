package com.och.ai.asr.config;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseVendorConfig {
    @Bean
    @ConditionalOnMissingBean(DatabaseIdProvider.class)
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties vendors = new Properties();
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("PostgreSQL", "postgresql");
        vendors.setProperty("Kingbase", "postgresql");
        provider.setProperties(vendors);
        return provider;
    }
}
