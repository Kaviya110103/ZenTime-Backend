package com.example.demo.tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.stereotype.Component;

@Component
public class TenantDataSourceRegistry {
    private static final String MYSQL_JDBC_PREFIX = "jdbc:mysql://";

    private final DataSource masterDataSource;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String datasourceDriver;
    private final boolean routingEnabled;
    private final Map<String, DataSource> cache = new ConcurrentHashMap<>();

    public TenantDataSourceRegistry(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String datasourceDriver,
            @Value("${tenant.routing.enabled:false}") boolean routingEnabled) {
        this.masterDataSource = masterDataSource;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.datasourceDriver = datasourceDriver;
        this.routingEnabled = routingEnabled;
    }

    public DataSource getMasterDataSource() {
        return masterDataSource;
    }

    public DataSource resolveTenantDataSource(String tenantDb) {
        if (!routingEnabled || tenantDb == null || tenantDb.isBlank()) {
            return masterDataSource;
        }
        return cache.computeIfAbsent(tenantDb, this::buildTenantDataSource);
    }

    private DataSource buildTenantDataSource(String tenantDb) {
        String tenantUrl = buildJdbcUrlForDatabase(tenantDb);
        return DataSourceBuilder.create()
                .driverClassName(datasourceDriver)
                .url(tenantUrl)
                .username(datasourceUsername)
                .password(datasourcePassword)
                .build();
    }

    private String buildJdbcUrlForDatabase(String dbName) {
        if (datasourceUrl == null || !datasourceUrl.startsWith(MYSQL_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Only MySQL JDBC URL is supported");
        }

        String body = datasourceUrl.substring(MYSQL_JDBC_PREFIX.length());
        int queryStart = body.indexOf('?');
        String queryPart = queryStart >= 0 ? body.substring(queryStart) : "";
        String hostAndPath = queryStart >= 0 ? body.substring(0, queryStart) : body;
        int slashIndex = hostAndPath.indexOf('/');
        String hostPort = slashIndex >= 0 ? hostAndPath.substring(0, slashIndex) : hostAndPath;
        return MYSQL_JDBC_PREFIX + hostPort + "/" + dbName + queryPart;
    }
}
