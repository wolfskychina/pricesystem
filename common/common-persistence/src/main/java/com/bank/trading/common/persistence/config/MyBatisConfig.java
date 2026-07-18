package com.bank.trading.common.persistence.config;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
@MapperScan(basePackages = "com.bank.trading.**.mapper")
public class MyBatisConfig {

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("SQLite", "sqlite");
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("MySQL", "mysql");
        provider.setProperties(properties);
        return provider;
    }
}
