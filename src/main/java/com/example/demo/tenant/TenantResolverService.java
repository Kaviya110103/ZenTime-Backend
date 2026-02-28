package com.example.demo.tenant;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class TenantResolverService {
    private final JdbcTemplate jdbcTemplate;
    private final boolean routingEnabled;
    private final String tenantDbHeader;
    private final String clientIdHeader;
    private final String clientIdQueryParam;

    public TenantResolverService(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Value("${tenant.routing.enabled:false}") boolean routingEnabled,
            @Value("${tenant.routing.header-db:X-Tenant-Db}") String tenantDbHeader,
            @Value("${tenant.routing.header-client:X-Client-Id}") String clientIdHeader,
            @Value("${tenant.routing.param-client:clientId}") String clientIdQueryParam) {
        this.jdbcTemplate = new JdbcTemplate(masterDataSource);
        this.routingEnabled = routingEnabled;
        this.tenantDbHeader = tenantDbHeader;
        this.clientIdHeader = clientIdHeader;
        this.clientIdQueryParam = clientIdQueryParam;
    }

    public String resolveTenantDb(HttpServletRequest request) {
        if (!routingEnabled) {
            return null;
        }

        String explicitTenantDb = request.getHeader(tenantDbHeader);
        if (StringUtils.hasText(explicitTenantDb)) {
            return explicitTenantDb.trim();
        }

        String clientIdValue = request.getHeader(clientIdHeader);
        if (!StringUtils.hasText(clientIdValue)) {
            clientIdValue = request.getHeader("clientId");
        }
        if (!StringUtils.hasText(clientIdValue)) {
            clientIdValue = request.getParameter(clientIdQueryParam);
        }
        if (!StringUtils.hasText(clientIdValue)) {
            return null;
        }

        Long clientId;
        try {
            clientId = Long.parseLong(clientIdValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }

        List<String> rows = jdbcTemplate.query(
                "SELECT tenant_db_name FROM clients WHERE id = ? AND provisioning_status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString(1),
                clientId
        );

        return rows.isEmpty() ? null : rows.get(0);
    }
}
