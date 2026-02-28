package com.example.demo.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import com.example.demo.tenant.TenantDataSourceRegistry;
import com.example.demo.tenant.TenantRoutingDataSource;

@Configuration
public class TenantDataSourceConfig {

    @Bean(name = "masterDataSource")
    public DataSource masterDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("masterDataSource") DataSource masterDataSource,
                                 TenantDataSourceRegistry registry) {
        return new TenantRoutingDataSource(registry);
    }
}
