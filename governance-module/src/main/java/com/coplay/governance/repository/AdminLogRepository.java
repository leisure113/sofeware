package com.coplay.governance.repository;

import com.coplay.governance.domain.AdminLog;

import java.util.List;

/**
 * Persistence port for administrator audit logs.
 */
public interface AdminLogRepository {

    long nextId();

    AdminLog save(AdminLog log);

    List<AdminLog> findAll();
}
