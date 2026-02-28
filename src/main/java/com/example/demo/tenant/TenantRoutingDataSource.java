package com.example.demo.tenant;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.AbstractDataSource;

public class TenantRoutingDataSource extends AbstractDataSource {
    private final TenantDataSourceRegistry registry;

    public TenantRoutingDataSource(TenantDataSourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return currentDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return currentDataSource().getConnection(username, password);
    }

    private DataSource currentDataSource() {
        String tenantDb = TenantContext.getTenantDb();
        return registry.resolveTenantDataSource(tenantDb);
    }
}
