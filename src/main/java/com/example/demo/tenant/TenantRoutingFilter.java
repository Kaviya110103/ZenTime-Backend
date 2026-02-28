package com.example.demo.tenant;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantRoutingFilter extends OncePerRequestFilter {
    private final TenantResolverService tenantResolverService;
    private final boolean routingEnabled;

    public TenantRoutingFilter(
            TenantResolverService tenantResolverService,
            @Value("${tenant.routing.enabled:false}") boolean routingEnabled) {
        this.tenantResolverService = tenantResolverService;
        this.routingEnabled = routingEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!routingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantDb = tenantResolverService.resolveTenantDb(request);
            if (tenantDb != null && !tenantDb.isBlank()) {
                TenantContext.setTenantDb(tenantDb);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
