package com.example.demo.tenant;

public final class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT_DB = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantDb(String tenantDb) {
        CURRENT_TENANT_DB.set(tenantDb);
    }

    public static String getTenantDb() {
        return CURRENT_TENANT_DB.get();
    }

    public static void clear() {
        CURRENT_TENANT_DB.remove();
    }
}
