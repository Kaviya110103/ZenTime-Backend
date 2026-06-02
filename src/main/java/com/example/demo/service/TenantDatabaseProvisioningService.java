package com.example.demo.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TenantDatabaseProvisioningService {

    private static final String MYSQL_JDBC_PREFIX = "jdbc:mysql://";

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${tenant.database.prefix:tenant_}")
    private String tenantDatabasePrefix;

    public void createDatabaseForCompanyCode(String companyCode) {
        String tenantDatabaseName = buildTenantDatabaseName(companyCode);
        createDatabaseIfMissing(tenantDatabaseName);
    }

    public void provisionTenantDatabase(String companyCode) {
        String tenantDatabaseName = buildTenantDatabaseName(companyCode);
        createDatabaseIfMissing(tenantDatabaseName);
        cloneSchemaFromPrimaryDatabase(tenantDatabaseName);
    }

    public String buildTenantDatabaseName(String companyCode) {
        return buildTenantDbName(companyCode);
    }

    public boolean tenantDatabaseExists(String tenantDatabaseName) {
        String serverJdbcUrl = buildServerJdbcUrl(datasourceUrl);
        String sql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";

        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, datasourceUsername, datasourcePassword);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantDatabaseName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify tenant database '" + tenantDatabaseName + "'", e);
        }
    }

    public void dropTenantDatabase(String tenantDatabaseName) {
        if (tenantDatabaseName == null || tenantDatabaseName.isBlank()) {
            return;
        }

        String sourceDatabase = extractDatabaseName(datasourceUrl);
        if (!tenantDatabaseName.startsWith(tenantDatabasePrefix)
                || tenantDatabaseName.equalsIgnoreCase(sourceDatabase)
                || !tenantDatabaseName.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Refusing to drop invalid tenant database name.");
        }

        String serverJdbcUrl = buildServerJdbcUrl(datasourceUrl);
        String sql = "DROP DATABASE IF EXISTS `" + tenantDatabaseName + "`";
        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, datasourceUsername, datasourcePassword);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to drop tenant database '" + tenantDatabaseName + "'", e);
        }
    }

    public boolean hasRequiredTables(String tenantDatabaseName, Set<String> requiredTables) {
        if (requiredTables == null || requiredTables.isEmpty()) {
            return true;
        }

        String tenantJdbcUrl = buildDatabaseJdbcUrl(tenantDatabaseName);
        try (Connection connection = DriverManager.getConnection(tenantJdbcUrl, datasourceUsername, datasourcePassword)) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : requiredTables) {
                try (ResultSet rs = metaData.getTables(tenantDatabaseName, null, table, new String[] { "TABLE" })) {
                    if (!rs.next()) {
                        return false;
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to verify tenant schema for '" + tenantDatabaseName + "'", e);
        }
    }

    private void createDatabaseIfMissing(String tenantDatabaseName) {
        String serverJdbcUrl = buildServerJdbcUrl(datasourceUrl);

        String sql = "CREATE DATABASE IF NOT EXISTS `" + tenantDatabaseName
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";

        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, datasourceUsername, datasourcePassword);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create tenant database '" + tenantDatabaseName + "'", e);
        }
    }

    private void cloneSchemaFromPrimaryDatabase(String tenantDatabaseName) {
        String sourceDatabase = extractDatabaseName(datasourceUrl);
        if (sourceDatabase == null || sourceDatabase.isBlank()) {
            throw new IllegalArgumentException("Primary database name could not be resolved from datasource URL");
        }

        List<String> sourceTables = listSourceTables(sourceDatabase);
        if (sourceTables.isEmpty()) {
            throw new IllegalStateException("No source tables found in primary database '" + sourceDatabase + "'");
        }

        String serverJdbcUrl = buildServerJdbcUrl(datasourceUrl);
        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, datasourceUsername, datasourcePassword);
             Statement statement = connection.createStatement()) {
            for (String tableName : sourceTables) {
                String cloneSql = "CREATE TABLE IF NOT EXISTS `" + tenantDatabaseName + "`.`" + tableName
                        + "` LIKE `" + sourceDatabase + "`.`" + tableName + "`";
                statement.executeUpdate(cloneSql);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clone tenant schema for database '" + tenantDatabaseName + "'", e);
        }
    }

    private List<String> listSourceTables(String sourceDatabase) {
        String serverJdbcUrl = buildServerJdbcUrl(datasourceUrl);
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE'";

        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, datasourceUsername, datasourcePassword);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sourceDatabase);

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            Collections.sort(tables);
            return tables;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list source tables for database '" + sourceDatabase + "'", e);
        }
    }

    private String buildTenantDbName(String companyCode) {
        if (companyCode == null || companyCode.isBlank()) {
            throw new IllegalArgumentException("Company code is required to create tenant database");
        }

        String normalizedCompanyCode = companyCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        normalizedCompanyCode = normalizedCompanyCode.replaceAll("_+", "_");
        normalizedCompanyCode = normalizedCompanyCode.replaceAll("^_+", "").replaceAll("_+$", "");

        if (normalizedCompanyCode.isBlank()) {
            throw new IllegalArgumentException("Company code contains no valid characters for database name");
        }

        String dbName = tenantDatabasePrefix + normalizedCompanyCode;
        if (dbName.length() > 64) {
            dbName = dbName.substring(0, 64);
        }
        return dbName;
    }

    private String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(MYSQL_JDBC_PREFIX)) {
            return null;
        }

        String body = jdbcUrl.substring(MYSQL_JDBC_PREFIX.length());
        int queryStart = body.indexOf('?');
        String hostAndPath = queryStart >= 0 ? body.substring(0, queryStart) : body;
        int slashIndex = hostAndPath.indexOf('/');
        if (slashIndex < 0 || slashIndex == hostAndPath.length() - 1) {
            return null;
        }
        return hostAndPath.substring(slashIndex + 1);
    }

    private String buildDatabaseJdbcUrl(String dbName) {
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

    private String buildServerJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(MYSQL_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Only MySQL JDBC URL is supported");
        }

        String body = jdbcUrl.substring(MYSQL_JDBC_PREFIX.length());
        int queryStart = body.indexOf('?');
        String queryPart = queryStart >= 0 ? body.substring(queryStart) : "";
        String hostAndPath = queryStart >= 0 ? body.substring(0, queryStart) : body;

        int slashIndex = hostAndPath.indexOf('/');
        String hostPort = slashIndex >= 0 ? hostAndPath.substring(0, slashIndex) : hostAndPath;

        return MYSQL_JDBC_PREFIX + hostPort + "/" + queryPart;
    }
}
